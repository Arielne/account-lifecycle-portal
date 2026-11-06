package io.github.arielne.alp.ticket;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.arielne.alp.MinioTestcontainersConfiguration;
import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import io.github.arielne.alp.common.ApiErrorResponse;
import io.github.arielne.alp.common.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Test chậm cho tạo phiếu (spec phiếu mục 13, test 10, 11, 18, 21): gọi API thật qua MockMvc trên Postgres, Redis,
 * MinIO của Testcontainers (Camunda thật, file lưu thật). Không có @Transactional: các request phải commit thật thì
 * mới thử được 2 request cùng lúc, nên mỗi test tự tạo dữ liệu riêng với mã không trùng (dữ liệu giả, CCCD bắt đầu
 * bằng 099 là mã tỉnh không có, QT-12). Context này có thêm MinIO nên dùng database riêng, không ảnh hưởng test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
        MinioTestcontainersConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class TicketCreateIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);
    private static final byte[] PDF_CONTENT = "%PDF-1.7 giay to gia".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ApiExceptionHandler apiExceptionHandler;

    private long branchId;
    private long accountTypeId;
    private String makerKeycloakId;

    @BeforeEach
    void setUp() {
        createBucketIfMissing();
        int number = SEQUENCE.incrementAndGet();
        branchId = jdbcTemplate.queryForObject("INSERT INTO app.branch (code, name, address, phone)"
                + " VALUES (?, ?, 'Địa chỉ giả', '0280000000') RETURNING id", Long.class, "IT" + number,
                "Chi nhánh test " + number);
        accountTypeId = jdbcTemplate.queryForObject("INSERT INTO app.account_type (code, name, dormant_after_months)"
                + " VALUES (?, 'Loại test', 12) RETURNING id", Long.class, "T" + number);
        makerKeycloakId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                + " VALUES (CAST(? AS uuid), ?, 'Giao Dịch Viên Test', ?, 'ACTIVE')", makerKeycloakId,
                "gdv" + number + "@nhdemo.local", branchId);
    }

    @Test
    @DisplayName("Mở tài khoản cho khách mới, họ tên dạng NFD: hồ sơ lưu NFC, tìm 'binh' ra; tài khoản Chờ mở;"
            + " phiếu Chờ duyệt; quy trình dừng ở bước duyệt; file trong MinIO tên UUID; có lịch sử, audit")
    void openAccountForNewCustomer() throws Exception {
        // Chuẩn bị: họ tên ở dạng NFD như khi dán từ file PDF
        String cccd = newCccd();
        String nfdName = Normalizer.normalize("Đỗ  Thị Bình", Normalizer.Form.NFD);

        // Chạy
        MvcResult result = mockMvc.perform(createTicket(newCustomerJson(cccd, nfdName)).with(maker()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();

        // Kiểm tra kết quả: hồ sơ khách lưu NFC, tìm không dấu ra
        long ticketId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        Map<String, Object> customer = jdbcTemplate.queryForMap(
                "SELECT id, full_name FROM app.customer WHERE cccd = ?", cccd);
        assertEquals("Đỗ Thị Bình", customer.get("full_name"));
        mockMvc.perform(post("/api/customers/search").with(maker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"searchBy\": \"NAME\", \"keyword\": \"binh\"}"))
                .andExpect(jsonPath("$.items[*].id", hasItem(((Number) customer.get("id")).intValue())));
        // Phiếu, tài khoản
        Map<String, Object> ticket = jdbcTemplate.queryForMap("SELECT t.status, t.branch_id, t.new_customer,"
                + " t.last_submitted_at = t.created_at AS sent_at_creation, t.process_instance_id, a.status AS account_status"
                + " FROM app.ticket t JOIN app.account a ON a.id = t.account_id WHERE t.id = ?", ticketId);
        assertEquals("PENDING_APPROVAL", ticket.get("status"));
        assertEquals(branchId, ((Number) ticket.get("branch_id")).longValue());
        assertEquals(true, ticket.get("new_customer"));
        assertEquals(true, ticket.get("sent_at_creation"));
        assertEquals("PENDING_OPEN", ticket.get("account_status"));
        // Quy trình Camunda dừng ở bước Kiểm soát viên duyệt
        Task task = taskService.createTaskQuery().processInstanceId((String) ticket.get("process_instance_id"))
                .singleResult();
        assertEquals("review", task.getTaskDefinitionKey());
        // File nằm trong MinIO với tên UUID, đọc lại đúng nội dung
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM app.attachment WHERE ticket_id = ?", String.class, ticketId);
        assertTrue(storageKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertArrayEquals(PDF_CONTENT,
                s3Client.getObjectAsBytes(builder -> builder.bucket("giay-to").key(storageKey)).asByteArray());
        // Lịch sử và audit
        assertEquals(1, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'SUBMITTED'",
                ticketId));
        assertEquals(1, count("SELECT count(*) FROM app.audit_log a JOIN app.ticket t ON t.code = a.object_id"
                + " WHERE t.id = ? AND a.object_type = 'TICKET' AND a.action = 'CREATE'", ticketId));
    }

    @Test
    @DisplayName("D6, D5: số CCCD thuộc hồ sơ chưa từng mở tài khoản thì ghi đè hồ sơ đó (giữ id); phiếu đang chờ"
            + " thì customer-check báo PENDING và tạo phiếu thứ 2 bị 409")
    void reusableProfileIsOverwrittenThenBecomesPending() throws Exception {
        // Chuẩn bị: hồ sơ cũ có 1 tài khoản Đã đóng, chưa bao giờ Hoạt động (opened_at rỗng)
        String cccd = newCccd();
        long oldCustomerId = createCustomer(cccd, "Tên Cũ Gõ Nhầm");
        createAccount(oldCustomerId, "CLOSED", null);

        // Chạy: phiếu mới cho khách mới cùng số CCCD
        mockMvc.perform(createTicket(newCustomerJson(cccd, "Trần Văn Mới")).with(maker()).with(csrf()))
                .andExpect(status().isCreated());

        // Kiểm tra kết quả: vẫn 1 hồ sơ, đúng id cũ, tên mới; giờ hồ sơ đang chờ duyệt
        assertEquals(1, count("SELECT count(*) FROM app.customer WHERE cccd = ?", cccd));
        assertEquals("Trần Văn Mới", jdbcTemplate.queryForObject(
                "SELECT full_name FROM app.customer WHERE id = ?", String.class, oldCustomerId));
        mockMvc.perform(post("/api/tickets/customer-check").with(maker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cccd\": \"" + cccd + "\"}"))
                .andExpect(jsonPath("$.state").value("PENDING"));
        mockMvc.perform(createTicket(newCustomerJson(cccd, "Trần Văn Mới")).with(maker()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("QT-03: tài khoản đã có phiếu chờ duyệt thì phiếu thứ 2 bị 409; 2 request cùng lúc chỉ 1 thành công")
    void accountGetsAtMostOneOpenTicket() throws Exception {
        // Chuẩn bị: 2 tài khoản Hoạt động
        long customerId = createCustomer(newCccd(), "Khách Hai Tài Khoản");
        String firstAccount = createAccount(customerId, "ACTIVE", "2024-01-10");
        String secondAccount = createAccount(customerId, "ACTIVE", "2024-01-10");

        // Chạy: tài khoản 1 gửi lần lượt; tài khoản 2 gửi 2 request cùng lúc
        int firstStatus = mockMvc.perform(createTicket(blockJson(firstAccount)).with(maker()).with(csrf()))
                .andReturn().getResponse().getStatus();
        MvcResult duplicate = mockMvc.perform(createTicket(blockJson(firstAccount)).with(maker()).with(csrf()))
                .andReturn();
        List<Integer> concurrentStatuses = sendTogether(blockJson(secondAccount), blockJson(secondAccount));

        // Kiểm tra kết quả
        assertEquals(201, firstStatus);
        assertEquals(409, duplicate.getResponse().getStatus());
        assertTrue(duplicate.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("chờ duyệt"));
        assertTrue(concurrentStatuses.contains(201) && concurrentStatuses.contains(409),
                "Kết quả 2 request cùng lúc: " + concurrentStatuses);
        assertEquals(1, count("SELECT count(*) FROM app.ticket t JOIN app.account a ON a.id = t.account_id"
                + " WHERE a.account_number = ?", secondAccount));
    }

    @Test
    @DisplayName("QT-10: tải giấy tờ qua backend ra đúng nội dung; giấy tờ của phiếu khác gắn vào URL phiếu này thì 404")
    void attachmentIsServedOnlyForItsOwnTicket() throws Exception {
        // Chuẩn bị: 2 phiếu, mỗi phiếu 1 giấy tờ
        long customerId = createCustomer(newCccd(), "Khách Tải Giấy Tờ");
        long firstTicket = createdTicketId(createAccount(customerId, "ACTIVE", "2024-01-10"));
        long secondTicket = createdTicketId(createAccount(customerId, "ACTIVE", "2024-01-10"));
        long secondAttachment = jdbcTemplate.queryForObject(
                "SELECT id FROM app.attachment WHERE ticket_id = ?", Long.class, secondTicket);

        // Chạy, kiểm tra kết quả
        mockMvc.perform(get("/api/tickets/" + secondTicket + "/attachments/" + secondAttachment).with(maker()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF_CONTENT));
        mockMvc.perform(get("/api/tickets/" + firstTicket + "/attachments/" + secondAttachment).with(maker()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Mục 11.3: trùng CCCD trên Postgres thật thì log và message lỗi không có số CCCD; API trả câu nghiệp vụ")
    void duplicateCccdErrorDoesNotLeakCccd(CapturedOutput output) {
        // Chuẩn bị: một hồ sơ đã có số CCCD này
        String cccd = newCccd();
        createCustomer(cccd, "Khách Đã Có");
        DataIntegrityViolationException exception = null;

        // Chạy: thêm hồ sơ thứ 2 cùng số, rồi đưa lỗi cho handler như khi lỗi bay ra khỏi Controller
        try {
            createCustomer(cccd, "Khách Trùng Số");
        } catch (DataIntegrityViolationException duplicateError) {
            exception = duplicateError;
        }
        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleDataIntegrityViolation(exception);

        // Kiểm tra kết quả
        assertFalse(exception.getMessage().contains(cccd), "Message của driver không được chứa số CCCD");
        assertEquals(409, response.getStatusCode().value());
        assertEquals("Số CCCD đã có hồ sơ khách hàng", response.getBody().message());
        assertTrue(output.getAll().contains("customer_cccd_key"));
        assertFalse(output.getAll().contains(cccd), "Log không được chứa số CCCD");
    }

    /** Gửi 2 request tạo phiếu cùng lúc từ 2 thread, trả mã HTTP của từng request. */
    private List<Integer> sendTogether(String firstJson, String secondJson) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (String json : List.of(firstJson, secondJson)) {
                Callable<Integer> request = () -> {
                    startSignal.await();
                    return mockMvc.perform(createTicket(json).with(maker()).with(csrf()))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(executor.submit(request));
            }
            startSignal.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            executor.shutdown();
        }
    }

    private long createdTicketId(String accountNumber) throws Exception {
        MvcResult result = mockMvc.perform(createTicket(blockJson(accountNumber)).with(maker()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockMultipartHttpServletRequestBuilder createTicket(String json) {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "giay-to.pdf", "application/pdf", PDF_CONTENT);
        return multipart("/api/tickets").file(data).file(file);
    }

    private String blockJson(String accountNumber) {
        return """
                {"type": "PHONG_TOA", "accountNumber": "%s", "reason": "Khách báo mất thẻ", "confirmed": true}
                """.formatted(accountNumber);
    }

    private String newCustomerJson(String cccd, String fullName) {
        return """
                {"type": "MO_TAI_KHOAN", "accountTypeId": %d, "reason": "Khách mở tài khoản", "confirmed": true,
                 "newCustomer": {"fullName": "%s", "dateOfBirth": "1990-03-15", "gender": "Nữ", "cccd": "%s",
                   "idIssueDate": "2021-04-10", "idIssuePlace": "Cục CS QLHC về TTXH", "phone": "0900000002",
                   "email": "khach@nhdemo.local", "address": "12 Đường Giả, Quận 1"}}
                """.formatted(accountTypeId, fullName, cccd);
    }

    private RequestPostProcessor maker() {
        return oidcLogin().idToken(token -> token.subject(makerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"));
    }

    private long createCustomer(String cccd, String fullName) {
        return jdbcTemplate.queryForObject("INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date,"
                + " id_issue_place, phone, email, address, branch_id) VALUES (?, ?, '1990-01-01', '2021-01-01',"
                + " 'Nơi cấp giả', '0900000001', 'khach.test@nhdemo.local', 'Địa chỉ giả', ?) RETURNING id",
                Long.class, cccd, fullName, branchId);
    }

    /** Tài khoản với số không trùng; openedAt rỗng nghĩa là chưa từng Hoạt động. Trả số tài khoản. */
    private String createAccount(long customerId, String status, String openedAt) {
        String accountNumber = "10299" + String.format("%05d", SEQUENCE.incrementAndGet());
        jdbcTemplate.update("INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id,"
                + " status, opened_at, last_transaction_at) VALUES (?, ?, ?, ?, ?, CAST(? AS date), CURRENT_DATE)",
                accountNumber, customerId, accountTypeId, branchId, status, openedAt);
        return accountNumber;
    }

    private String newCccd() {
        return "0990" + String.format("%08d", SEQUENCE.incrementAndGet());
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