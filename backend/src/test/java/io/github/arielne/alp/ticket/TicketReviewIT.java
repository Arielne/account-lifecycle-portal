package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.arielne.alp.MinioTestcontainersConfiguration;
import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Test chậm cho duyệt, từ chối và màn Chờ duyệt (spec phiếu mục 13, test 12, 13, 14, 16, 17, 19): gọi API thật qua
 * MockMvc trên Postgres, Redis, MinIO của Testcontainers, Camunda chạy thật trong cùng transaction. Không có
 * @Transactional vì phải commit thật mới thử được 2 người duyệt cùng lúc; mỗi test tự tạo chi nhánh, nhân viên, khách
 * riêng. Cùng cấu hình container với TicketCreateIT nên Spring dùng lại context đó; số đếm bắt đầu ở 5000 để mã chi
 * nhánh, số tài khoản, CCCD không trùng với dữ liệu của TicketCreateIT (dữ liệu giả, CCCD 0991..., QT-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
        MinioTestcontainersConfiguration.class})
class TicketReviewIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(5000);
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
    private String checkerKeycloakId;
    private long checkerId;

    @BeforeEach
    void setUp() {
        createBucketIfMissing();
        branchId = createBranch();
        accountTypeId = jdbcTemplate.queryForObject("INSERT INTO app.account_type (code, name, dormant_after_months)"
                + " VALUES (?, 'Loại test', 12) RETURNING id", Long.class, "R" + SEQUENCE.incrementAndGet());
        makerKeycloakId = UUID.randomUUID().toString();
        createEmployee(makerKeycloakId, branchId);
        checkerKeycloakId = UUID.randomUUID().toString();
        checkerId = createEmployee(checkerKeycloakId, branchId);
    }

    @Test
    @DisplayName("Duyệt phiếu Phong tỏa: phiếu Đã duyệt, tài khoản Phong tỏa, có lịch sử trạng thái, 1 dòng outbox,"
            + " quy trình dừng ở bước chờ gửi mail, có audit")
    void approveBlocksAccount() throws Exception {
        // Chuẩn bị
        String accountNumber = createAccount(createCustomer(), "ACTIVE", "2024-01-10");
        long ticketId = createTicket(blockJson(accountNumber));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticketId + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả: phiếu, tài khoản
        Map<String, Object> ticket = jdbcTemplate.queryForMap("SELECT t.status, t.reviewed_by, t.reviewed_at,"
                + " t.process_instance_id, t.code, a.status AS account_status, a.id AS account_id"
                + " FROM app.ticket t JOIN app.account a ON a.id = t.account_id WHERE t.id = ?", ticketId);
        assertEquals("APPROVED", ticket.get("status"));
        assertEquals(checkerId, ((Number) ticket.get("reviewed_by")).longValue());
        assertTrue(ticket.get("reviewed_at") != null);
        assertEquals("BLOCKED", ticket.get("account_status"));
        // Lịch sử trạng thái tài khoản: Hoạt động → Phong tỏa, theo phiếu này, người đổi là người duyệt
        assertEquals(1, count("SELECT count(*) FROM app.account_status_history WHERE account_id = ?"
                + " AND from_status = 'ACTIVE' AND to_status = 'BLOCKED' AND ticket_id = ? AND changed_by = ?",
                ticket.get("account_id"), ticketId, checkerId));
        // Outbox: đúng 1 sự kiện, payload chỉ có id và mã phiếu
        assertEquals(1, count("SELECT count(*) FROM app.outbox_event WHERE aggregate_type = 'TICKET'"
                + " AND aggregate_id = ? AND event_type = 'TICKET_APPROVED' AND published_at IS NULL"
                + " AND payload = jsonb_build_object('ticketId', CAST(? AS bigint), 'ticketCode', CAST(? AS text))",
                String.valueOf(ticketId), ticketId, ticket.get("code")));
        // Quy trình dừng ở bước chờ gửi mail xong (T8)
        List<String> activities = runtimeService.getActiveActivityIds((String) ticket.get("process_instance_id"));
        assertEquals(List.of("mailSent"), activities);
        // Lịch sử xử lý và audit
        assertEquals(1, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'APPROVED'",
                ticketId));
        assertEquals(1, count("SELECT count(*) FROM app.audit_log WHERE object_type = 'TICKET' AND action = 'APPROVE'"
                + " AND object_id = ?", ticket.get("code")));
    }

    @Test
    @DisplayName("Duyệt phiếu Mở tài khoản: tài khoản Chờ mở sang Hoạt động, ngày mở là hôm nay; Vòng đời có 2 mục")
    void approveOpensPendingAccount() throws Exception {
        // Chuẩn bị
        long ticketId = createTicket(newCustomerJson(newCccd()));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticketId + "/approve").with(checker()).with(csrf()))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả
        Map<String, Object> account = jdbcTemplate.queryForMap("SELECT a.id, a.status,"
                + " a.opened_at = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS opened_today"
                + " FROM app.ticket t JOIN app.account a ON a.id = t.account_id WHERE t.id = ?", ticketId);
        assertEquals("ACTIVE", account.get("status"));
        assertEquals(true, account.get("opened_today"));
        List<String> lifecycle = jdbcTemplate.queryForList("SELECT coalesce(from_status, '-') || '>' || to_status"
                + " FROM app.account_status_history WHERE account_id = ? ORDER BY id", String.class, account.get("id"));
        assertEquals(List.of("->PENDING_OPEN", "PENDING_OPEN>ACTIVE"), lifecycle);
    }

    @Test
    @DisplayName("D7, test 19: trạng thái tài khoản đổi sau khi tạo phiếu thì duyệt nhận 409 và mọi thứ bị hủy:"
            + " phiếu vẫn Chờ duyệt, không có lịch sử, outbox, bước duyệt vẫn còn")
    void approveRollsBackWhenAccountStatusChanged() throws Exception {
        // Chuẩn bị: phiếu Phong tỏa tạo lúc Hoạt động, rồi job chuyển Ngủ đông (sửa thẳng bằng SQL)
        String accountNumber = createAccount(createCustomer(), "ACTIVE", "2024-01-10");
        long ticketId = createTicket(blockJson(accountNumber));
        jdbcTemplate.update("UPDATE app.account SET status = 'DORMANT' WHERE account_number = ?", accountNumber);

        // Chạy
        MvcResult result = mockMvc.perform(post("/api/tickets/" + ticketId + "/approve").with(checker()).with(csrf()))
                .andReturn();

        // Kiểm tra kết quả
        assertEquals(409, result.getResponse().getStatus());
        assertEquals("Trạng thái tài khoản đã đổi từ Hoạt động sang Ngủ đông sau khi tạo phiếu. Hãy từ chối phiếu này.",
                JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.message"));
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, process_instance_id FROM app.ticket WHERE id = ?", ticketId);
        assertEquals("PENDING_APPROVAL", ticket.get("status"));
        assertEquals(null, ticket.get("reviewed_by"));
        assertEquals("DORMANT", jdbcTemplate.queryForObject(
                "SELECT status FROM app.account WHERE account_number = ?", String.class, accountNumber));
        assertEquals(0, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'APPROVED'",
                ticketId));
        assertEquals(0, count("SELECT count(*) FROM app.outbox_event WHERE aggregate_id = ?",
                String.valueOf(ticketId)));
        assertEquals("review", taskService.createTaskQuery()
                .processInstanceId((String) ticket.get("process_instance_id")).singleResult().getTaskDefinitionKey());
    }

    @Test
    @DisplayName("QT-11, test 13: 2 kiểm soát viên cùng duyệt một phiếu thì 1 thành công, 1 nhận 409; chỉ 1 dòng outbox")
    void concurrentApprovalsLetOnlyOneWin() throws Exception {
        // Chuẩn bị: thêm kiểm soát viên thứ 2 cùng chi nhánh
        String accountNumber = createAccount(createCustomer(), "ACTIVE", "2024-01-10");
        long ticketId = createTicket(blockJson(accountNumber));
        String secondCheckerKeycloakId = UUID.randomUUID().toString();
        createEmployee(secondCheckerKeycloakId, branchId);

        // Chạy
        List<MvcResult> results = approveTogether(ticketId, checker(), checkerWithId(secondCheckerKeycloakId));

        // Kiểm tra kết quả
        List<Integer> statuses = new ArrayList<>();
        String conflictMessage = null;
        for (MvcResult result : results) {
            statuses.add(result.getResponse().getStatus());
            if (result.getResponse().getStatus() == 409) {
                conflictMessage = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                        "$.message");
            }
        }
        assertTrue(statuses.contains(204) && statuses.contains(409), "Kết quả 2 lần duyệt cùng lúc: " + statuses);
        assertEquals("Phiếu này vừa được người khác xử lý", conflictMessage);
        assertEquals(1, count("SELECT count(*) FROM app.outbox_event WHERE aggregate_id = ?",
                String.valueOf(ticketId)));
        assertEquals(1, count("SELECT count(*) FROM app.account_status_history h JOIN app.ticket t"
                + " ON t.id = h.ticket_id WHERE t.id = ? AND h.to_status = 'BLOCKED'", ticketId));
    }

    @Test
    @DisplayName("Từ chối: phiếu Bị từ chối kèm lý do, outbox TICKET_REJECTED, quy trình chờ người tạo sửa,"
            + " tài khoản giữ nguyên")
    void rejectReturnsTicketToCreator() throws Exception {
        // Chuẩn bị
        String accountNumber = createAccount(createCustomer(), "ACTIVE", "2024-01-10");
        long ticketId = createTicket(blockJson(accountNumber));

        // Chạy
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reject").with(checker()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  Đơn chưa có chữ ký  \"}"))
                .andExpect(status().isNoContent());

        // Kiểm tra kết quả
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "SELECT status, reject_reason, process_instance_id FROM app.ticket WHERE id = ?", ticketId);
        assertEquals("REJECTED", ticket.get("status"));
        assertEquals("Đơn chưa có chữ ký", ticket.get("reject_reason"));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM app.account WHERE account_number = ?", String.class, accountNumber));
        assertEquals(1, count("SELECT count(*) FROM app.outbox_event WHERE aggregate_id = ?"
                + " AND event_type = 'TICKET_REJECTED'", String.valueOf(ticketId)));
        assertEquals("revise", taskService.createTaskQuery()
                .processInstanceId((String) ticket.get("process_instance_id")).singleResult().getTaskDefinitionKey());
        assertEquals(1, count("SELECT count(*) FROM app.ticket_history WHERE ticket_id = ? AND action = 'REJECTED'"
                + " AND note = 'Đơn chưa có chữ ký'", ticketId));
    }

    @Test
    @DisplayName("QT-01: người giữ cả MAKER và CHECKER tự tạo phiếu thì duyệt nhận 403; chi tiết phiếu có nút khóa")
    void creatorWithBothRolesCannotApprove() throws Exception {
        // Chuẩn bị: phiếu do Giao dịch viên tạo, người đó cũng có role CHECKER
        String accountNumber = createAccount(createCustomer(), "ACTIVE", "2024-01-10");
        long ticketId = createTicket(blockJson(accountNumber));
        RequestPostProcessor creatorWithBothRoles = oidcLogin().idToken(token -> token.subject(makerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"), new SimpleGrantedAuthority("ROLE_CHECKER"));

        // Chạy, kiểm tra kết quả
        mockMvc.perform(post("/api/tickets/" + ticketId + "/approve").with(creatorWithBothRoles).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không thể duyệt phiếu do chính mình tạo"));
        mockMvc.perform(get("/api/tickets/" + ticketId).with(creatorWithBothRoles))
                .andExpect(jsonPath("$.canApprove").value(false))
                .andExpect(jsonPath("$.blockedReason").value("Bạn không thể duyệt phiếu do chính mình tạo"));
        assertEquals("PENDING_APPROVAL", jdbcTemplate.queryForObject(
                "SELECT status FROM app.ticket WHERE id = ?", String.class, ticketId));
    }

    @Test
    @DisplayName("Test 16, 17: Chờ duyệt chỉ có phiếu chờ của chi nhánh mình, không có phiếu tự tạo; quá 24 giờ là"
            + " hơn hẳn 24 giờ; gửi sớm nhất ở trên; badge đếm đúng")
    void pendingReviewListsOnlyReviewableTicketsOldestFirst() throws Exception {
        // Chuẩn bị: 2 phiếu chờ (gửi cách đây 24 giờ 1 phút và 23 giờ 59 phút), 1 phiếu đã từ chối,
        // 1 phiếu của chi nhánh khác, 1 phiếu do chính kiểm soát viên tạo
        long olderTicket = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE", "2024-01-10")));
        long newerTicket = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE", "2024-01-10")));
        long rejectedTicket = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE", "2024-01-10")));
        jdbcTemplate.update("UPDATE app.ticket SET last_submitted_at = now() - interval '24 hours 1 minute'"
                + " WHERE id = ?", olderTicket);
        jdbcTemplate.update("UPDATE app.ticket SET last_submitted_at = now() - interval '23 hours 59 minutes'"
                + " WHERE id = ?", newerTicket);
        jdbcTemplate.update("UPDATE app.ticket SET status = 'REJECTED' WHERE id = ?", rejectedTicket);
        long ownTicket = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE", "2024-01-10")));
        jdbcTemplate.update("UPDATE app.ticket SET created_by = ? WHERE id = ?", checkerId, ownTicket);
        long otherBranchTicket = createTicket(blockJson(createAccount(createCustomer(), "ACTIVE", "2024-01-10")));
        jdbcTemplate.update("UPDATE app.ticket SET branch_id = ? WHERE id = ?", createBranch(), otherBranchTicket);

        // Chạy
        MvcResult list = mockMvc.perform(get("/api/tickets/pending-review").with(checker()))
                .andExpect(status().isOk()).andReturn();
        MvcResult summary = mockMvc.perform(get("/api/tickets/pending-review/summary").with(checker()))
                .andExpect(status().isOk()).andReturn();

        // Kiểm tra kết quả
        String json = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> ids = JsonPath.read(json, "$[*].id");
        assertEquals(List.of(olderTicket, newerTicket), ids.stream().map(Number::longValue).toList());
        assertEquals(List.of(true, false), JsonPath.read(json, "$[*].overdue"));
        assertEquals(1441, ((Number) JsonPath.read(json, "$[0].waitingMinutes")).intValue());
        String summaryJson = summary.getResponse().getContentAsString();
        assertEquals(2, ((Number) JsonPath.read(summaryJson, "$.pendingCount")).intValue());
        assertEquals(1, ((Number) JsonPath.read(summaryJson, "$.overdueCount")).intValue());
    }

    /** 2 kiểm soát viên bấm Duyệt cùng lúc từ 2 thread. */
    private List<MvcResult> approveTogether(long ticketId, RequestPostProcessor first, RequestPostProcessor second)
            throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (RequestPostProcessor reviewer : List.of(first, second)) {
                Callable<MvcResult> request = () -> {
                    startSignal.await();
                    return mockMvc.perform(post("/api/tickets/" + ticketId + "/approve").with(reviewer).with(csrf()))
                            .andReturn();
                };
                futures.add(executor.submit(request));
            }
            startSignal.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
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

    private String newCustomerJson(String cccd) {
        return """
                {"type": "MO_TAI_KHOAN", "accountTypeId": %d, "reason": "Khách mở tài khoản", "confirmed": true,
                 "newCustomer": {"fullName": "Lê Văn Duyệt", "dateOfBirth": "1990-03-15", "gender": "Nam", "cccd": "%s",
                   "idIssueDate": "2021-04-10", "idIssuePlace": "Cục CS QLHC về TTXH", "phone": "0900000003",
                   "email": "khach.duyet@nhdemo.local", "address": "12 Đường Giả, Quận 1"}}
                """.formatted(accountTypeId, cccd);
    }

    private RequestPostProcessor maker() {
        return oidcLogin().idToken(token -> token.subject(makerKeycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"));
    }

    private RequestPostProcessor checker() {
        return checkerWithId(checkerKeycloakId);
    }

    private RequestPostProcessor checkerWithId(String keycloakId) {
        return oidcLogin().idToken(token -> token.subject(keycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"));
    }

    private long createBranch() {
        int number = SEQUENCE.incrementAndGet();
        return jdbcTemplate.queryForObject("INSERT INTO app.branch (code, name, address, phone)"
                + " VALUES (?, ?, 'Địa chỉ giả', '0280000000') RETURNING id", Long.class, "IT" + number,
                "Chi nhánh test " + number);
    }

    private long createEmployee(String keycloakId, long employeeBranchId) {
        return jdbcTemplate.queryForObject("INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                + " VALUES (CAST(? AS uuid), ?, 'Nhân Viên Test', ?, 'ACTIVE') RETURNING id", Long.class, keycloakId,
                "nv" + SEQUENCE.incrementAndGet() + "@nhdemo.local", employeeBranchId);
    }

    private long createCustomer() {
        return jdbcTemplate.queryForObject("INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date,"
                + " id_issue_place, phone, email, address, branch_id) VALUES (?, 'Khách Duyệt Phiếu', '1990-01-01',"
                + " '2021-01-01', 'Nơi cấp giả', '0900000001', 'khach.test@nhdemo.local', 'Địa chỉ giả', ?)"
                + " RETURNING id", Long.class, newCccd(), branchId);
    }

    /** Tài khoản với số không trùng; trả số tài khoản. */
    private String createAccount(long customerId, String status, String openedAt) {
        String accountNumber = "10298" + String.format("%05d", SEQUENCE.incrementAndGet());
        jdbcTemplate.update("INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id,"
                + " status, opened_at, last_transaction_at) VALUES (?, ?, ?, ?, ?, CAST(? AS date), CURRENT_DATE)",
                accountNumber, customerId, accountTypeId, branchId, status, openedAt);
        return accountNumber;
    }

    private String newCccd() {
        return "0991" + String.format("%08d", SEQUENCE.incrementAndGet());
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