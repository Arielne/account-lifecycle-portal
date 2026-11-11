package io.github.arielne.alp.ticket;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.arielne.alp.MinioTestcontainersConfiguration;
import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Test chậm cho sửa và gửi lại, hủy phiếu, Tất cả phiếu và 4 chỗ nối (spec phiếu mục 13, test 14, 15, 22): gọi API
 * thật qua MockMvc trên Postgres, Redis, MinIO của Testcontainers, Camunda chạy thật. Không có @Transactional (các
 * request commit thật); mỗi test tự tạo chi nhánh, nhân viên, khách riêng. Cùng cấu hình container với TicketCreateIT,
 * TicketReviewIT nên Spring dùng lại context; số đếm bắt đầu ở 8000 để dữ liệu không trùng (dữ liệu giả, CCCD 0992...).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
        MinioTestcontainersConfiguration.class})
class TicketRevisionIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(8000);
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
    private RuntimeService runtimeService;

    private long branchId;
    private long accountTypeId;
    private String makerKeycloakId;
    private long makerId;
    private String checkerKeycloakId;

    @BeforeEach
    void setUp() {
        createBucketIfMissing();
        branchId = createBranch();
        accountTypeId = jdbcTemplate.queryForObject("INSERT INTO app.account_type (code, name, dormant_after_months)"
                + " VALUES (?, 'Loại test', 12) RETURNING id", Long.class, "S" + SEQUENCE.incrementAndGet());
        makerKeycloakId = UUID.randomUUID().toString();
        makerId = createEmployee(makerKeycloakId, branchId, "Người Tạo Phiếu");
        checkerKeycloakId = UUID.randomUUID().toString();
        createEmployee(checkerKeycloakId, branchId, "Người Duyệt Phiếu");
    }

    @Test
    @DisplayName("Test 14: từ chối rồi gửi lại, bỏ giấy tờ cũ và thêm giấy tờ mới: phiếu Chờ duyệt, gửi lần 2, quy trình về"
            + " bước duyệt; giấy tờ bị bỏ vẫn còn trên Storage nhưng không còn ở chi tiết, tải thì 404 (D3)")
    void resubmitReturnsTicketToReview() throws Exception {
        // Chuẩn bị: phiếu bị từ chối, có 1 giấy tờ cũ
        long ticketId = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE")));
        reject(ticketId);
        Map<String, Object> oldAttachment = jdbcTemplate.queryForMap(
                "SELECT id, storage_key FROM app.attachment WHERE ticket_id = ?", ticketId);

        // Chạy: bỏ giấy tờ cũ, thêm 1 giấy tờ mới
        String json = """
                {"reason": "Đã bổ sung chữ ký", "removedAttachmentIds": [%d], "confirmed": true}
                """.formatted(((Number) oldAttachment.get("id")).longValue());
        mockMvc.perform(resubmit(ticketId, json, "don-moi.pdf").with(maker()).with(csrf()))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả: phiếu
        Map<String, Object> ticket = jdbcTemplate.queryForMap("SELECT status, submit_count, reviewed_by, reject_reason,"
                + " last_submitted_at > created_at AS sent_again, process_instance_id FROM app.ticket WHERE id = ?",
                ticketId);
        assertEquals("PENDING_APPROVAL", ticket.get("status"));
        assertEquals(2, ((Number) ticket.get("submit_count")).intValue());
        assertEquals(null, ticket.get("reviewed_by"));
        assertEquals(null, ticket.get("reject_reason"));
        assertEquals(true, ticket.get("sent_again"));
        assertEquals("review", taskService.createTaskQuery()
                .processInstanceId((String) ticket.get("process_instance_id")).singleResult().getTaskDefinitionKey());
        // Giấy tờ: cũ bị bỏ mềm, file vẫn còn; chi tiết chỉ còn giấy tờ mới; tải giấy tờ cũ thì 404
        String storageKey = (String) oldAttachment.get("storage_key");
        assertArrayEquals(PDF_CONTENT,
                s3Client.getObjectAsBytes(builder -> builder.bucket("giay-to").key(storageKey)).asByteArray());
        mockMvc.perform(get("/api/tickets/" + ticketId).with(maker()))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].originalName").value("don-moi.pdf"));
        mockMvc.perform(get("/api/tickets/" + ticketId + "/attachments/" + oldAttachment.get("id")).with(maker()))
                .andExpect(status().isNotFound());
        assertEquals("Bỏ giấy tờ: giay-to.pdf · Thêm giấy tờ: don-moi.pdf", jdbcTemplate.queryForObject(
                "SELECT note FROM app.ticket_history WHERE ticket_id = ? AND action = 'RESUBMITTED'", String.class,
                ticketId));
    }

    @Test
    @DisplayName("D7: tài khoản đổi trạng thái trong lúc phiếu Bị từ chối thì gửi lại nhận 409, phiếu vẫn Bị từ chối")
    void resubmitAfterAccountChangedIsRejected() throws Exception {
        // Chuẩn bị
        String accountNumber = createAccount(createCustomer(), "ACTIVE");
        long ticketId = createTicket(blockJson(accountNumber));
        reject(ticketId);
        jdbcTemplate.update("UPDATE app.account SET status = 'DORMANT' WHERE account_number = ?", accountNumber);

        // Chạy
        MvcResult result = mockMvc.perform(resubmit(ticketId, reasonOnlyJson(), "don-moi.pdf").with(maker())
                .with(csrf())).andReturn();

        // Kiểm tra kết quả
        assertEquals(409, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Hãy hủy phiếu này"));
        assertEquals("REJECTED", jdbcTemplate.queryForObject("SELECT status FROM app.ticket WHERE id = ?",
                String.class, ticketId));
    }

    @Test
    @DisplayName("D2: phiếu khách mới bị từ chối: edit-data trả hồ sơ đầy đủ cho người tạo (no-store), người khác 403;"
            + " gửi lại với họ tên NFD thì hồ sơ lưu NFC; đổi sang CCCD của hồ sơ khác thì 409")
    void resubmitNewCustomerUpdatesProfile() throws Exception {
        // Chuẩn bị: phiếu Mở tài khoản cho khách mới, bị từ chối; một hồ sơ khác đã có số CCCD "đã dùng"
        String cccd = newCccd();
        long ticketId = createTicket(newCustomerJson(cccd, "Lê Văn Sai"));
        reject(ticketId);
        String usedCccd = jdbcTemplate.queryForObject("SELECT cccd FROM app.customer WHERE id = ?", String.class,
                createCustomer());
        String otherMakerKeycloakId = UUID.randomUUID().toString();
        createEmployee(otherMakerKeycloakId, branchId, "Giao Dịch Viên Khác");

        // Chạy, kiểm tra kết quả: edit-data
        mockMvc.perform(get("/api/tickets/" + ticketId + "/edit-data").with(maker()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.newCustomer.cccd").value(cccd));
        mockMvc.perform(get("/api/tickets/" + ticketId + "/edit-data").with(makerWithId(otherMakerKeycloakId)))
                .andExpect(status().isForbidden());

        // Chạy, kiểm tra kết quả: CCCD trùng hồ sơ khác thì 409; sửa đúng thì hồ sơ lưu NFC
        mockMvc.perform(resubmit(ticketId, editNewCustomerJson(usedCccd, "Lê Văn Đúng"), null).with(maker())
                .with(csrf())).andExpect(status().isConflict());
        String nfdName = Normalizer.normalize("Lê  Văn Đúng", Normalizer.Form.NFD);
        mockMvc.perform(resubmit(ticketId, editNewCustomerJson(cccd, nfdName), null).with(maker()).with(csrf()))
                .andExpect(status().isNoContent());
        Map<String, Object> customer = jdbcTemplate.queryForMap(
                "SELECT full_name, gender, phone FROM app.customer WHERE cccd = ?", cccd);
        assertEquals("Lê Văn Đúng", customer.get("full_name"));
        assertEquals("Nam", customer.get("gender"));
        assertEquals("0900000004", customer.get("phone"));
    }

    @Test
    @DisplayName("Test 15: hủy phiếu Mở tài khoản: tài khoản Đã đóng, quy trình bị xóa, hồ sơ khách giữ lại (D6),"
            + " Vòng đời có mục Chờ mở → Đã đóng kèm lý do hủy")
    void cancelOpeningClosesPendingAccount() throws Exception {
        // Chuẩn bị
        String cccd = newCccd();
        long ticketId = createTicket(newCustomerJson(cccd, "Khách Hủy Phiếu"));
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "SELECT process_instance_id, account_id FROM app.ticket WHERE id = ?", ticketId);
        long accountId = ((Number) ticket.get("account_id")).longValue();

        // Chạy
        mockMvc.perform(cancel(ticketId, "Khách đổi ý").with(maker())).andExpect(status().isNoContent());

        // Kiểm tra kết quả
        assertEquals("CANCELLED", jdbcTemplate.queryForObject("SELECT status FROM app.ticket WHERE id = ?",
                String.class, ticketId));
        assertEquals(true, jdbcTemplate.queryForObject("SELECT status = 'CLOSED' AND closed_at IS NOT NULL"
                + " FROM app.account WHERE id = ?", Boolean.class, accountId));
        assertEquals(0, runtimeService.createProcessInstanceQuery()
                .processInstanceId((String) ticket.get("process_instance_id")).count());
        assertEquals(1, count("SELECT count(*) FROM app.customer WHERE cccd = ?", cccd));
        mockMvc.perform(get("/api/accounts/" + accountId + "/status-history").with(maker()))
                .andExpect(jsonPath("$[0].fromStatus").value("PENDING_OPEN"))
                .andExpect(jsonPath("$[0].toStatus").value("CLOSED"))
                .andExpect(jsonPath("$[0].reason").value("Khách đổi ý"))
                .andExpect(jsonPath("$[1].toStatus").value("PENDING_OPEN"));
    }

    @Test
    @DisplayName("D12: Giao dịch viên khác hủy phiếu thì 403; người tạo bị khóa thì người cùng chi nhánh hủy được (không sửa"
            + " được), lịch sử ghi người hủy thật; người khác chi nhánh vẫn 403")
    void cancelOnBehalfOfLockedCreator() throws Exception {
        // Chuẩn bị: phiếu bị từ chối; một Giao dịch viên cùng chi nhánh, một ở chi nhánh khác
        String accountNumber = createAccount(createCustomer(), "ACTIVE");
        long ticketId = createTicket(blockJson(accountNumber));
        reject(ticketId);
        String colleagueKeycloakId = UUID.randomUUID().toString();
        long colleagueId = createEmployee(colleagueKeycloakId, branchId, "Đồng Nghiệp Cùng Chi Nhánh");
        String outsiderKeycloakId = UUID.randomUUID().toString();
        createEmployee(outsiderKeycloakId, createBranch(), "Giao Dịch Viên Chi Nhánh Khác");

        // Chạy, kiểm tra kết quả: người tạo còn làm việc thì đồng nghiệp không hủy được
        mockMvc.perform(cancel(ticketId, "Hủy thử").with(makerWithId(colleagueKeycloakId)))
                .andExpect(status().isForbidden());
        // Người tạo bị khóa: đồng nghiệp không sửa được, người chi nhánh khác không hủy được, đồng nghiệp hủy được
        jdbcTemplate.update("UPDATE app.employee SET status = 'LOCKED' WHERE id = ?", makerId);
        mockMvc.perform(resubmit(ticketId, reasonOnlyJson(), "don-moi.pdf").with(makerWithId(colleagueKeycloakId))
                .with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(cancel(ticketId, "Hủy thử").with(makerWithId(outsiderKeycloakId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/" + ticketId).with(makerWithId(colleagueKeycloakId)))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.cancelForLockedCreator").value(true))
                .andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(cancel(ticketId, "Người tạo đã nghỉ việc").with(makerWithId(colleagueKeycloakId)))
                .andExpect(status().isNoContent());
        assertEquals(colleagueId, jdbcTemplate.queryForObject("SELECT actor_id FROM app.ticket_history"
                + " WHERE ticket_id = ? AND action = 'CANCELLED'", Long.class, ticketId));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject("SELECT status FROM app.account WHERE account_number = ?",
                String.class, accountNumber));
    }

    @Test
    @DisplayName("Test 22: Tất cả phiếu lọc theo tài khoản, khách hàng, khoảng ngày theo giờ Việt Nam; số đếm theo trạng"
            + " thái không tính bộ lọc trạng thái (D8); chi tiết tài khoản có Mở bởi, Duyệt mở bởi")
    void ticketListAndAccountConnections() throws Exception {
        // Chuẩn bị: khách có 2 tài khoản; tài khoản 1 có 1 phiếu đã hủy và 1 phiếu chờ; tài khoản 2 có 1 phiếu chờ
        long customerId = createCustomer();
        String firstAccount = createAccount(customerId, "ACTIVE");
        long cancelledTicket = createTicket(blockJson(firstAccount));
        mockMvc.perform(cancel(cancelledTicket, "Tạo nhầm").with(maker())).andExpect(status().isNoContent());
        long pendingTicket = createTicket(blockJson(firstAccount));
        createTicket(blockJson(createAccount(customerId, "ACTIVE")));
        long firstAccountId = jdbcTemplate.queryForObject("SELECT id FROM app.account WHERE account_number = ?",
                Long.class, firstAccount);
        // Phiếu chờ tạo lúc 23 giờ 30 ngày 6/11 giờ UTC, tức 6 giờ 30 sáng ngày 7/11 giờ Việt Nam
        jdbcTemplate.update("UPDATE app.ticket SET created_at = '2026-11-06T23:30:00Z' WHERE id = ?", pendingTicket);

        // Chạy, kiểm tra kết quả: theo tài khoản, theo khách hàng
        mockMvc.perform(get("/api/tickets").param("accountId", String.valueOf(firstAccountId)).with(checker()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.statusCounts.CANCELLED").value(1))
                .andExpect(jsonPath("$.statusCounts.PENDING_APPROVAL").value(1));
        mockMvc.perform(get("/api/tickets").param("customerId", String.valueOf(customerId))
                        .param("statuses", "PENDING_APPROVAL").with(checker()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.statusCounts.CANCELLED").value(1));
        // Khoảng ngày tính theo giờ Việt Nam
        mockMvc.perform(get("/api/tickets").param("accountId", String.valueOf(firstAccountId))
                        .param("createdFrom", "2026-11-07").param("createdTo", "2026-11-07").with(checker()))
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) pendingTicket)));
        mockMvc.perform(get("/api/tickets").param("accountId", String.valueOf(firstAccountId))
                        .param("createdFrom", "2026-11-06").param("createdTo", "2026-11-06").with(checker()))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem((int) pendingTicket))));

        // Mở bởi, Duyệt mở bởi: phiếu Mở tài khoản được duyệt
        long openingTicket = createTicket(newCustomerJson(newCccd(), "Khách Mới Được Duyệt"));
        mockMvc.perform(post("/api/tickets/" + openingTicket + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());
        long openedAccountId = jdbcTemplate.queryForObject("SELECT account_id FROM app.ticket WHERE id = ?",
                Long.class, openingTicket);
        mockMvc.perform(get("/api/accounts/" + openedAccountId).with(maker()))
                .andExpect(jsonPath("$.openedByName").value("Người Tạo Phiếu"))
                .andExpect(jsonPath("$.openApprovedByName").value("Người Duyệt Phiếu"));
        mockMvc.perform(get("/api/accounts/" + openedAccountId + "/status-history").with(maker()))
                .andExpect(jsonPath("$[0].toStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].actorName").value("Người Duyệt Phiếu"))
                .andExpect(jsonPath("$[1].toStatus").value("PENDING_OPEN"))
                .andExpect(jsonPath("$[1].actorName").value("Người Tạo Phiếu"));
    }

    private void reject(long ticketId) throws Exception {
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reject").with(checker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Đơn chưa có chữ ký\"}"))
                .andExpect(status().isNoContent());
    }

    /** PUT multipart: MockMvc chỉ có multipart() cho POST, nên đổi method thành PUT. fileName null là không thêm file. */
    private MockMultipartHttpServletRequestBuilder resubmit(long ticketId, String json, String fileName) {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/tickets/" + ticketId).file(data);
        if (fileName != null) {
            builder.file(new MockMultipartFile("files", fileName, "application/pdf", PDF_CONTENT));
        }
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private MockHttpServletRequestBuilder cancel(long ticketId, String reason) {
        return post("/api/tickets/" + ticketId + "/cancel").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}");
    }

    /** Giao dịch viên tạo phiếu qua API thật, trả id phiếu. */
    private long createTicket(String json) throws Exception {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "giay-to.pdf", "application/pdf", PDF_CONTENT);
        MvcResult result = mockMvc.perform(multipart("/api/tickets").file(data).file(file).with(maker()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String blockJson(String accountNumber) {
        return """
                {"type": "PHONG_TOA", "accountNumber": "%s", "reason": "Khách báo mất thẻ", "confirmed": true}
                """.formatted(accountNumber);
    }

    private String reasonOnlyJson() {
        return """
                {"reason": "Đã bổ sung chữ ký", "confirmed": true}
                """;
    }

    private String newCustomerJson(String cccd, String fullName) {
        return """
                {"type": "MO_TAI_KHOAN", "accountTypeId": %d, "reason": "Khách mở tài khoản", "confirmed": true,
                 "newCustomer": %s}
                """.formatted(accountTypeId, customerJson(cccd, fullName));
    }

    private String editNewCustomerJson(String cccd, String fullName) {
        return """
                {"accountTypeId": %d, "reason": "Đã sửa hồ sơ", "confirmed": true, "newCustomer": %s}
                """.formatted(accountTypeId, customerJson(cccd, fullName));
    }

    private String customerJson(String cccd, String fullName) {
        return """
                {"fullName": "%s", "dateOfBirth": "1990-03-15", "gender": "Nam", "cccd": "%s",
                 "idIssueDate": "2021-04-10", "idIssuePlace": "Cục CS QLHC về TTXH", "phone": "0900000004",
                 "email": "khach.sua@nhdemo.local", "address": "12 Đường Giả, Quận 1"}
                """.formatted(fullName, cccd);
    }

    private RequestPostProcessor maker() {
        return makerWithId(makerKeycloakId);
    }

    private RequestPostProcessor makerWithId(String keycloakId) {
        return oidcLogin().idToken(token -> token.subject(keycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"));
    }

    private RequestPostProcessor checker() {
        return oidcLogin().idToken(token -> token.subject(checkerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"));
    }

    private long createBranch() {
        int number = SEQUENCE.incrementAndGet();
        return jdbcTemplate.queryForObject("INSERT INTO app.branch (code, name, address, phone)"
                + " VALUES (?, ?, 'Địa chỉ giả', '0280000000') RETURNING id", Long.class, "IT" + number,
                "Chi nhánh test " + number);
    }

    private long createEmployee(String keycloakId, long employeeBranchId, String fullName) {
        return jdbcTemplate.queryForObject("INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                + " VALUES (CAST(? AS uuid), ?, ?, ?, 'ACTIVE') RETURNING id", Long.class, keycloakId,
                "nv" + SEQUENCE.incrementAndGet() + "@nhdemo.local", fullName, employeeBranchId);
    }

    private long createCustomer() {
        return jdbcTemplate.queryForObject("INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date,"
                + " id_issue_place, phone, email, address, branch_id) VALUES (?, 'Khách Sửa Phiếu', '1990-01-01',"
                + " '2021-01-01', 'Nơi cấp giả', '0900000001', 'khach.test@nhdemo.local', 'Địa chỉ giả', ?)"
                + " RETURNING id", Long.class, newCccd(), branchId);
    }

    /** Tài khoản với số không trùng; trả số tài khoản. */
    private String createAccount(long customerId, String status) {
        String accountNumber = "10297" + String.format("%05d", SEQUENCE.incrementAndGet());
        jdbcTemplate.update("INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id,"
                + " status, opened_at, last_transaction_at) VALUES (?, ?, ?, ?, ?, '2024-01-10', CURRENT_DATE)",
                accountNumber, customerId, accountTypeId, branchId, status);
        return accountNumber;
    }

    private String newCccd() {
        return "0992" + String.format("%08d", SEQUENCE.incrementAndGet());
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