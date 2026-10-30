package io.github.arielne.alp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test chậm cho phần ghi audit log bằng AOP (spec admin mục 8, test 9).
 * Gọi API thật qua MockMvc (đủ Security, filter mã request, Controller, Service, AuditAspect) trên Postgres
 * của Testcontainers. @MockitoSpyBean bọc AuditLogMapper thật: mặc định chạy như thật, test cuối bắt nó ném lỗi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class AuditLogIT {

    private static final String ADMIN_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000a1";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditLogMapper auditLogMapper;

    @Test
    @DisplayName("Thêm chi nhánh thì có đúng 1 dòng audit: đúng người làm, hành động, đối tượng, có mã request")
    void createBranchWritesOneAuditRow() throws Exception {
        // Chuẩn bị: hồ sơ của Admin đang đăng nhập
        Long adminEmployeeId = insertAdminEmployee();

        // Chạy
        mockMvc.perform(post("/api/branches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("ZZ-Q7", "Chi nhánh Quận 7", "12 Nguyễn Thị Thập", "02838720011")))
                .andExpect(status().isCreated());

        // Kiểm tra kết quả
        assertEquals(1, countAuditRows("ZZ-Q7"));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM app.audit_log WHERE object_type = 'BRANCH' AND object_id = 'ZZ-Q7'");
        assertEquals(adminEmployeeId, row.get("employee_id"));
        assertEquals("CREATE", row.get("action"));
        assertEquals("Chi nhánh Quận 7", row.get("description"));
        assertEquals("127.0.0.1", row.get("ip_address"));
        assertEquals("BranchService.create", row.get("source"));
        assertTrue(((String) row.get("request_id")).matches(UUID_PATTERN));
    }

    @Test
    @DisplayName("Sửa 2 trường của chi nhánh thì cột changes có 2 mục, mô tả 'Sửa 2 trường: ...'")
    void updateTwoFieldsWritesTwoChanges() throws Exception {
        // Chuẩn bị: chi nhánh có sẵn
        insertAdminEmployee();
        Long branchId = jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone)"
                        + " VALUES ('ZZ-Q3', 'Chi nhánh Quận 3', '88 Trần Quốc Thảo', '02839324051') RETURNING id",
                Long.class);

        // Chạy: đổi địa chỉ và điện thoại
        mockMvc.perform(put("/api/branches/" + branchId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("ZZ-Q3", "Chi nhánh Quận 3", "210 Võ Văn Tần", "02839307712")))
                .andExpect(status().isOk());

        // Kiểm tra kết quả: jsonb_array_length đếm số phần tử của mảng JSON trong cột changes
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT action, description, jsonb_array_length(changes) AS change_count,"
                        + " changes -> 1 ->> 'after' AS phone_after"
                        + " FROM app.audit_log WHERE object_type = 'BRANCH' AND object_id = 'ZZ-Q3'");
        assertEquals("UPDATE", row.get("action"));
        assertEquals("Sửa 2 trường: Địa chỉ, Số điện thoại", row.get("description"));
        assertEquals(2, row.get("change_count"));
        assertEquals("028 3930 7712", row.get("phone_after"));
    }

    @Test
    @DisplayName("Thao tác bị từ chối (mã trùng) thì không có dòng audit nào")
    void rejectedOperationWritesNoAuditRow() throws Exception {
        // Chuẩn bị: mã ZZ-Q1 đã có
        insertAdminEmployee();
        jdbcTemplate.update("INSERT INTO app.branch (code, name, address, phone)"
                + " VALUES ('ZZ-Q1', 'Chi nhánh Quận 1', '45 Lê Thánh Tôn', '02838234510')");

        // Chạy
        mockMvc.perform(post("/api/branches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("ZZ-Q1", "Chi nhánh trùng mã", "Địa chỉ khác", "02838234511")))
                .andExpect(status().isConflict());

        // Kiểm tra kết quả
        assertEquals(0, countAuditRows("ZZ-Q1"));
    }

    /**
     * Test này không chạy trong transaction của test (NOT_SUPPORTED), để Service tự mở và tự hủy transaction thật.
     * Nhờ vậy thấy được: lưu audit lỗi thì chi nhánh vừa INSERT cũng bị hủy, vì hai câu nằm cùng một transaction.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Lưu audit lỗi giữa chừng thì chi nhánh vừa thêm cũng bị hủy, API trả 500 câu chung")
    void auditFailureRollsBackOperation() throws Exception {
        // Chuẩn bị: bắt AuditLogMapper.insert ném lỗi
        doThrow(new IllegalStateException("Giả lập lỗi khi lưu audit")).when(auditLogMapper).insert(any());

        // Chạy
        mockMvc.perform(post("/api/branches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("ZZ-RB", "Chi nhánh phải bị hủy", "Địa chỉ test", "02838000000")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống. Vui lòng thử lại sau."));

        // Kiểm tra kết quả
        Integer branchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.branch WHERE code = 'ZZ-RB'", Integer.class);
        assertEquals(0, branchCount);
        assertEquals(0, countAuditRows("ZZ-RB"));
    }

    private RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject(ADMIN_KEYCLOAK_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Long insertAdminEmployee() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.employee (keycloak_id, email, full_name, status)"
                        + " VALUES (CAST(? AS uuid), 'admin.test@nhdemo.local', 'Admin Test', 'ACTIVE') RETURNING id",
                Long.class, ADMIN_KEYCLOAK_ID);
    }

    private int countAuditRows(String branchCode) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.audit_log WHERE object_type = 'BRANCH' AND object_id = ?",
                Integer.class, branchCode);
    }

    private String branchJson(String code, String name, String address, String phone) {
        return """
                {"code": "%s", "name": "%s", "address": "%s", "phone": "%s"}
                """.formatted(code, name, address, phone);
    }
}