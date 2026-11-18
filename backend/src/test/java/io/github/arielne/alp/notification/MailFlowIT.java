package io.github.arielne.alp.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.arielne.alp.KafkaTestcontainersConfiguration;
import io.github.arielne.alp.MinioTestcontainersConfiguration;
import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import io.github.arielne.alp.employee.KeycloakUserClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Test chậm cho cả luồng mail (spec phiếu mục 9, mục 13): API duyệt, từ chối, gán role ghi outbox → OutboxPublisher
 * đẩy sang Kafka thật của Testcontainers → listener của notification gửi mail → phiếu duyệt sang Hoàn tất.
 * Bật lại 3 phần Kafka mà application.properties của test đã tắt. JavaMailSender là mock (không có Mailpit), ghi lại
 * mọi mail vào sentMails; KeycloakUserClient là mock (không có KeyCloak).
 * Mail đi từ luồng đọc Kafka, chạy song song với test, nên test hỏi lại kết quả nhiều lần cho tới khi có (waitUntil).
 * Không có @Transactional: dữ liệu phải commit thật thì OutboxPublisher (luồng khác) mới đọc được.
 */
@SpringBootTest(properties = {
        "alp.outbox.publisher-enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"})
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
        MinioTestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class MailFlowIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(8000);
    private static final byte[] PDF_CONTENT = "%PDF-1.7 giay to gia".getBytes(StandardCharsets.US_ASCII);
    private static final String ADMIN_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000c1";
    // Consumer của Kafka cần vài giây để vào nhóm ở lần đầu; chờ rộng tay để máy chậm (CI) không bị đỏ oan.
    private static final long MAX_WAIT_MILLIS = 60_000;
    private static final long POLL_MILLIS = 300;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private HistoryService historyService;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private KeycloakUserClient keycloakUserClient;

    // Luồng Kafka thêm mail vào, luồng của test đọc ra: CopyOnWriteArrayList là List an toàn khi nhiều luồng cùng dùng.
    private final List<SimpleMailMessage> sentMails = new CopyOnWriteArrayList<>();

    private long branchId;
    private long accountTypeId;
    private String makerKeycloakId;
    private String makerEmail;
    private String checkerKeycloakId;

    @BeforeEach
    void setUp() {
        createBucketIfMissing();
        doAnswer(recordMail()).when(mailSender).send(any(SimpleMailMessage.class));
        branchId = createBranch();
        accountTypeId = jdbcTemplate.queryForObject("INSERT INTO app.account_type (code, name, dormant_after_months)"
                + " VALUES (?, 'Loại test', 12) RETURNING id", Long.class, "M" + SEQUENCE.incrementAndGet());
        makerKeycloakId = UUID.randomUUID().toString();
        makerEmail = "gdv" + SEQUENCE.incrementAndGet() + "@nhdemo.local";
        createEmployee(makerKeycloakId, makerEmail);
        checkerKeycloakId = UUID.randomUUID().toString();
        createEmployee(checkerKeycloakId, "ksv" + SEQUENCE.incrementAndGet() + "@nhdemo.local");
    }

    @Test
    @DisplayName("Duyệt phiếu: khách và người tạo nhận mail qua Kafka, phiếu sang Hoàn tất, quy trình kết thúc ở bước"
            + " Hoàn tất, lịch sử và audit ghi người làm là Hệ thống")
    void approvedTicketSendsMailsAndCompletes() throws Exception {
        // Chuẩn bị
        String customerEmail = "khach" + SEQUENCE.incrementAndGet() + "@nhdemo.local";
        String accountNumber = createAccount(createCustomer(customerEmail));
        CreatedTicket ticket = createTicket(blockJson(accountNumber));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticket.id() + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả: phiếu sang Hoàn tất sau khi 2 mail đã gửi
        waitUntil("phiếu " + ticket.code() + " sang Hoàn tất", () -> "COMPLETED".equals(ticketStatus(ticket.id())));
        List<SimpleMailMessage> mails = mailsMentioning(ticket.code());
        assertEquals(2, mails.size());
        assertEquals(List.of(customerEmail), recipientsOf(mails.get(0)));
        assertEquals("Kết quả yêu cầu Phong tỏa", mails.get(0).getSubject());
        assertEquals(List.of(makerEmail), recipientsOf(mails.get(1)));
        assertEquals("Phiếu " + ticket.code() + " đã được duyệt", mails.get(1).getSubject());
        // Lịch sử xử lý và audit: bước Hoàn tất không có người làm, không có IP (Hệ thống, T3)
        assertEquals(1, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'COMPLETED'"
                + " AND from_status = 'APPROVED' AND to_status = 'COMPLETED' AND actor_id IS NULL", ticket.id()));
        assertEquals(1, count("SELECT count(*) FROM app.audit_log WHERE object_type = 'TICKET' AND action = 'COMPLETE'"
                + " AND object_id = ? AND employee_id IS NULL AND ip_address IS NULL AND description = ?",
                ticket.code(), "Đã gửi mail kết quả · Phong tỏa · TK " + accountNumber + " · Hoạt động → Phong tỏa"));
        // Outbox được đánh dấu đã gửi khi lượt gửi commit, có thể sau lúc listener đã xử lý xong, nên cũng phải chờ
        waitUntil("sự kiện của phiếu " + ticket.code() + " được đánh dấu đã gửi",
                () -> count("SELECT count(*) FROM app.outbox_event WHERE aggregate_type = 'TICKET' AND aggregate_id = ?"
                        + " AND published_at IS NULL", String.valueOf(ticket.id())) == 0);
        // Quy trình Camunda đi qua message mailSent tới bước kết thúc Hoàn tất
        HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId(ticket.id()))
                .singleResult();
        assertEquals("completed", process.getEndActivityId());
    }

    @Test
    @DisplayName("Sự kiện duyệt tới lần hai (Kafka giao lại): không gửi thêm mail, phiếu chỉ Hoàn tất một lần")
    void duplicateApprovedEventSendsNoMoreMail() throws Exception {
        // Chuẩn bị: phiếu đã duyệt xong và đã Hoàn tất
        String accountNumber = createAccount(createCustomer("khach" + SEQUENCE.incrementAndGet() + "@nhdemo.local"));
        CreatedTicket ticket = createTicket(blockJson(accountNumber));
        mockMvc.perform(post("/api/tickets/" + ticket.id() + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());
        waitUntil("phiếu " + ticket.code() + " sang Hoàn tất", () -> "COMPLETED".equals(ticketStatus(ticket.id())));

        // Chạy: chép lại sự kiện duyệt như khi Kafka giao lần hai, rồi từ chối một phiếu khác làm mốc. Hai sự kiện
        // cùng topic, topic có 1 partition nên consumer xử lý đúng thứ tự: thấy mail của phiếu mốc nghĩa là sự kiện
        // chép đã xử lý xong.
        jdbcTemplate.update("INSERT INTO app.outbox_event (aggregate_type, aggregate_id, event_type, payload)"
                + " SELECT aggregate_type, aggregate_id, event_type, payload FROM app.outbox_event"
                + " WHERE aggregate_type = 'TICKET' AND aggregate_id = ? AND event_type = 'TICKET_APPROVED'",
                String.valueOf(ticket.id()));
        CreatedTicket marker = createTicket(blockJson(createAccount(
                createCustomer("khach" + SEQUENCE.incrementAndGet() + "@nhdemo.local"))));
        mockMvc.perform(post("/api/tickets/" + marker.id() + "/reject").with(checker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Phiếu làm mốc\"}"))
                .andExpect(status().isNoContent());
        waitUntil("mail từ chối phiếu mốc " + marker.code(), () -> !mailsMentioning(marker.code()).isEmpty());

        // Kiểm tra kết quả: vẫn chỉ 2 mail và 1 bước Hoàn tất
        assertEquals(2, mailsMentioning(ticket.code()).size());
        assertEquals(1, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'COMPLETED'",
                ticket.id()));
    }

    @Test
    @DisplayName("Từ chối phiếu: người tạo nhận 1 mail kèm lý do, phiếu giữ Bị từ chối")
    void rejectedTicketSendsReasonToCreator() throws Exception {
        // Chuẩn bị
        String accountNumber = createAccount(createCustomer("khach" + SEQUENCE.incrementAndGet() + "@nhdemo.local"));
        CreatedTicket ticket = createTicket(blockJson(accountNumber));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticket.id() + "/reject").with(checker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Thiếu ảnh CCCD mặt sau\"}"))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả: mail đi khi listener đã xử lý xong sự kiện, nên lúc thấy mail thì không còn mail nào khác
        waitUntil("mail từ chối phiếu " + ticket.code(), () -> !mailsMentioning(ticket.code()).isEmpty());
        List<SimpleMailMessage> mails = mailsMentioning(ticket.code());
        assertEquals(1, mails.size());
        assertEquals(List.of(makerEmail), recipientsOf(mails.get(0)));
        assertEquals("Phiếu " + ticket.code() + " bị từ chối", mails.get(0).getSubject());
        assertTrue(mails.get(0).getText().contains("bị từ chối với lý do:\nThiếu ảnh CCCD mặt sau"));
        assertEquals("REJECTED", ticketStatus(ticket.id()));
    }

    @Test
    @DisplayName("Mailpit tắt lúc gửi lần đầu: Spring Kafka đọc lại sự kiện và gửi lại, phiếu vẫn sang Hoàn tất")
    void mailIsResentWhenMailServerWasDown() throws Exception {
        // Chuẩn bị: lần gửi đầu tiên lỗi như khi Mailpit tắt, các lần sau gửi được
        doThrow(new MailSendException("Mailpit tắt")).doAnswer(recordMail())
                .when(mailSender).send(any(SimpleMailMessage.class));
        String customerEmail = "khach" + SEQUENCE.incrementAndGet() + "@nhdemo.local";
        CreatedTicket ticket = createTicket(blockJson(createAccount(createCustomer(customerEmail))));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticket.id() + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả: lần thử lại gửi đủ cả 2 mail rồi phiếu mới sang Hoàn tất
        waitUntil("phiếu " + ticket.code() + " sang Hoàn tất", () -> "COMPLETED".equals(ticketStatus(ticket.id())));
        List<SimpleMailMessage> mails = mailsMentioning(ticket.code());
        assertEquals(2, mails.size());
        assertEquals(List.of(customerEmail), recipientsOf(mails.get(0)));
    }

    @Test
    @DisplayName("Gán role đầu tiên cho người Chờ cấp quyền: mail kích hoạt tài khoản đi qua Kafka tới đúng người đó")
    void firstRoleSendsActivationMailThroughKafka() throws Exception {
        // Chuẩn bị: người tự đăng ký, đã có chi nhánh, chưa có role trên KeyCloak
        String keycloakId = UUID.randomUUID().toString();
        String email = "moi" + SEQUENCE.incrementAndGet() + "@nhdemo.local";
        long employeeId = createEmployee(keycloakId, email);
        when(keycloakUserClient.findAppRoles(keycloakId)).thenReturn(List.of());

        // Chạy
        mockMvc.perform(put("/api/employees/" + employeeId + "/roles").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\": [\"MAKER\"]}"))
                .andExpect(status().isOk());

        // Kiểm tra kết quả
        waitUntil("mail kích hoạt tới " + email, () -> !mailsTo(email).isEmpty());
        SimpleMailMessage mail = mailsTo(email).get(0);
        assertEquals("Tài khoản đã được kích hoạt", mail.getSubject());
        assertTrue(mail.getText().contains("đã được cấp quyền: Giao dịch viên."));
    }

    /** Id và mã của phiếu vừa tạo qua API. */
    private record CreatedTicket(long id, String code) {
    }

    /** Việc mock JavaMailSender làm khi được gọi: ghi mail vào sentMails thay vì gửi thật. */
    private Answer<Void> recordMail() {
        return invocation -> {
            sentMails.add(invocation.getArgument(0));
            return null;
        };
    }

    /**
     * Hỏi lại condition mỗi 0,3 giây cho tới khi đúng; quá 60 giây thì test đỏ kèm mô tả việc đang chờ.
     * BooleanSupplier: một hàm không tham số trả true hoặc false, ở đây viết bằng lambda.
     */
    private void waitUntil(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Hết " + MAX_WAIT_MILLIS / 1000 + " giây mà chưa thấy: " + description);
            }
            Thread.sleep(POLL_MILLIS);
        }
    }

    /** Mail có mã phiếu ở tiêu đề hoặc nội dung, theo thứ tự gửi. */
    private List<SimpleMailMessage> mailsMentioning(String ticketCode) {
        List<SimpleMailMessage> mails = new ArrayList<>();
        for (SimpleMailMessage mail : sentMails) {
            if (mail.getSubject().contains(ticketCode) || mail.getText().contains(ticketCode)) {
                mails.add(mail);
            }
        }
        return mails;
    }

    private List<SimpleMailMessage> mailsTo(String email) {
        List<SimpleMailMessage> mails = new ArrayList<>();
        for (SimpleMailMessage mail : sentMails) {
            if (recipientsOf(mail).contains(email)) {
                mails.add(mail);
            }
        }
        return mails;
    }

    private List<String> recipientsOf(SimpleMailMessage mail) {
        return List.of(mail.getTo());
    }

    private String ticketStatus(long ticketId) {
        return jdbcTemplate.queryForObject("SELECT status FROM app.ticket WHERE id = ?", String.class, ticketId);
    }

    private String processInstanceId(long ticketId) {
        return jdbcTemplate.queryForObject("SELECT process_instance_id FROM app.ticket WHERE id = ?", String.class,
                ticketId);
    }

    /** Giao dịch viên tạo phiếu qua API thật. */
    private CreatedTicket createTicket(String json) throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "giay-to.pdf", "application/pdf", PDF_CONTENT);
        MvcResult result = mockMvc.perform(multipart("/api/tickets").file(data).file(file).with(maker()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        return new CreatedTicket(id, JsonPath.read(body, "$.code"));
    }

    private String blockJson(String accountNumber) {
        return """
                {"type": "PHONG_TOA", "accountNumber": "%s", "reason": "Khách báo mất thẻ", "confirmed": true}
                """.formatted(accountNumber);
    }

    private RequestPostProcessor maker() {
        return oidcLogin().idToken(token -> token.subject(makerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"));
    }

    private RequestPostProcessor checker() {
        return oidcLogin().idToken(token -> token.subject(checkerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"));
    }

    private RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject(ADMIN_KEYCLOAK_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private long createBranch() {
        int number = SEQUENCE.incrementAndGet();
        return jdbcTemplate.queryForObject("INSERT INTO app.branch (code, name, address, phone)"
                + " VALUES (?, ?, 'Địa chỉ giả', '0280000000') RETURNING id", Long.class, "MF" + number,
                "Chi nhánh test " + number);
    }

    private long createEmployee(String keycloakId, String email) {
        return jdbcTemplate.queryForObject("INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                + " VALUES (CAST(? AS uuid), ?, 'Nhân Viên Test', ?, 'ACTIVE') RETURNING id", Long.class, keycloakId,
                email, branchId);
    }

    private long createCustomer(String email) {
        String cccd = "0991" + String.format("%08d", SEQUENCE.incrementAndGet());
        return jdbcTemplate.queryForObject("INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date,"
                + " id_issue_place, phone, email, address, branch_id) VALUES (?, 'Khách Nhận Mail', '1990-01-01',"
                + " '2021-01-01', 'Nơi cấp giả', '0900000001', ?, 'Địa chỉ giả', ?) RETURNING id", Long.class, cccd,
                email, branchId);
    }

    /** Tài khoản Hoạt động với số không trùng; trả số tài khoản. */
    private String createAccount(long customerId) {
        String accountNumber = "10297" + String.format("%05d", SEQUENCE.incrementAndGet());
        jdbcTemplate.update("INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id,"
                + " status, opened_at, last_transaction_at) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_DATE, CURRENT_DATE)",
                accountNumber, customerId, accountTypeId, branchId);
        return accountNumber;
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private void createBucketIfMissing() {
        try {
            s3Client.headBucket(builder -> builder.bucket("giay-to"));
        } catch (NoSuchBucketException missing) {
            s3Client.createBucket(builder -> builder.bucket("giay-to"));
        }
    }
}