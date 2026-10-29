package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test chậm cho EmployeeMapper (spec mục 8, test 7): câu INSERT ... ON CONFLICT chạy thật trên Postgres của Testcontainers.
 * Test tự thêm dữ liệu riêng (không dùng db/seeds) và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class EmployeeMapperIT {

    private static final String KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-000000000001";

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Lần đăng nhập đầu thêm đúng 1 hồ sơ: status ACTIVE, chưa có chi nhánh")
    void firstLoginInsertsOneProfile() {
        // Chuẩn bị: chưa có hồ sơ nào với keycloak_id này

        // Chạy
        employeeMapper.upsertFromLogin(KEYCLOAK_ID, "moi.dang.ky@nhdemo.local", "Nhân Viên Mới");

        // Kiểm tra kết quả
        assertEquals(1, countProfiles());
        Employee employee = employeeMapper.findByKeycloakId(KEYCLOAK_ID);
        assertEquals("moi.dang.ky@nhdemo.local", employee.getEmail());
        assertEquals("Nhân Viên Mới", employee.getFullName());
        assertEquals("ACTIVE", employee.getStatus());
        assertNull(employee.getBranchId());
        assertNull(employee.getBranchName());
    }

    @Test
    @DisplayName("Lần đăng nhập sau không thêm dòng, chỉ cập nhật email và họ tên; chi nhánh và trạng thái giữ nguyên")
    void laterLoginUpdatesEmailAndNameOnly() {
        // Chuẩn bị: hồ sơ đã có, Admin đã gán chi nhánh và đã khóa
        Long branchId = jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone)"
                        + " VALUES ('TST-NV', 'Chi nhánh test', 'Địa chỉ test', '0900000000') RETURNING id",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                        + " VALUES (CAST(? AS uuid), 'email.cu@nhdemo.local', 'Tên Cũ', ?, 'LOCKED')",
                KEYCLOAK_ID, branchId);

        // Chạy: đăng nhập lại với email và họ tên mới trong token
        employeeMapper.upsertFromLogin(KEYCLOAK_ID, "email.moi@nhdemo.local", "Tên Mới");

        // Kiểm tra kết quả
        assertEquals(1, countProfiles());
        Employee employee = employeeMapper.findByKeycloakId(KEYCLOAK_ID);
        assertEquals("email.moi@nhdemo.local", employee.getEmail());
        assertEquals("Tên Mới", employee.getFullName());
        assertEquals(branchId, employee.getBranchId());
        assertEquals("TST-NV", employee.getBranchCode());
        assertEquals("LOCKED", employee.getStatus());
    }

    private int countProfiles() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.employee WHERE keycloak_id = CAST(? AS uuid)", Integer.class, KEYCLOAK_ID);
    }
}