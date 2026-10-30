package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.util.List;
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

    @Test
    @DisplayName("insert gán id mới; findByEmail, findStatusByKeycloakId đọc đúng; updateProfile bỏ được chi nhánh")
    void insertThenUpdateProfileAndStatus() {
        // Chuẩn bị: hồ sơ do Admin tạo, có chi nhánh
        Long branchId = jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone)"
                        + " VALUES ('TST-NV2', 'Chi nhánh test 2', 'Địa chỉ test', '0900000000') RETURNING id",
                Long.class);
        Employee employee = new Employee();
        employee.setKeycloakId(KEYCLOAK_ID);
        employee.setEmail("an.nv@nhdemo.local");
        employee.setFullName("Nguyễn Văn An");
        employee.setBranchId(branchId);
        employee.setStatus("ACTIVE");
        employeeMapper.insert(employee);

        // Chạy: đổi email, bỏ chi nhánh, rồi khóa
        employee.setEmail("an.moi@nhdemo.local");
        employee.setBranchId(null);
        employeeMapper.updateProfile(employee);
        employeeMapper.updateStatus(employee.getId(), "LOCKED");

        // Kiểm tra kết quả
        Employee saved = employeeMapper.findByEmail("an.moi@nhdemo.local");
        assertEquals(employee.getId(), saved.getId());
        assertNull(saved.getBranchId());
        assertNotNull(saved.getCreatedAt());
        assertEquals("LOCKED", employeeMapper.findStatusByKeycloakId(KEYCLOAK_ID));
        assertNull(employeeMapper.findByEmail("an.nv@nhdemo.local"));
        assertEquals("Chi nhánh test 2", employeeMapper.findBranchNameById(branchId));
    }

    @Test
    @DisplayName("findAll đọc hồ sơ kèm tên chi nhánh, người chưa có chi nhánh vẫn có trong danh sách")
    void findAllIncludesEmployeesWithoutBranch() {
        // Chuẩn bị
        employeeMapper.upsertFromLogin(KEYCLOAK_ID, "moi.dang.ky@nhdemo.local", "Nhân Viên Mới");

        // Chạy
        List<Employee> employees = employeeMapper.findAll();

        // Kiểm tra kết quả
        Employee newEmployee = employees.stream()
                .filter(employee -> employee.getKeycloakId().equals(KEYCLOAK_ID))
                .findFirst()
                .orElseThrow();
        assertNull(newEmployee.getBranchName());
        assertEquals("ACTIVE", newEmployee.getStatus());
    }

    private int countProfiles() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.employee WHERE keycloak_id = CAST(? AS uuid)", Integer.class, KEYCLOAK_ID);
    }
}