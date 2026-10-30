package io.github.arielne.alp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Test chậm cho BranchMapper (CN-21): câu SQL trong BranchMapper.xml chạy thật trên Postgres của Testcontainers.
 * Test tự thêm chi nhánh riêng (không dùng db/seeds) và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class BranchMapperIT {

    @Autowired
    private BranchMapper branchMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("findAll trả mọi chi nhánh, xếp theo mã, đọc đủ các cột vào Branch")
    void findAllReturnsBranchesSortedByCodeWithAllColumns() {
        // Chuẩn bị: thêm 2 chi nhánh, cố ý thêm mã ZZ-B trước ZZ-A
        insertBranch("ZZ-B", "Chi nhánh test B", "0900000002");
        insertBranch("ZZ-A", "Chi nhánh test A", "0900000001");

        // Chạy
        List<Branch> branches = branchMapper.findAll();

        // Kiểm tra kết quả: đúng thứ tự mã, cột created_at được đọc vào createdAt
        assertEquals(2, branches.size());
        Branch firstBranch = branches.get(0);
        assertEquals("ZZ-A", firstBranch.getCode());
        assertEquals("Chi nhánh test A", firstBranch.getName());
        assertEquals("Địa chỉ test", firstBranch.getAddress());
        assertEquals("0900000001", firstBranch.getPhone());
        assertNotNull(firstBranch.getId());
        assertNotNull(firstBranch.getCreatedAt());
        assertEquals("ZZ-B", branches.get(1).getCode());
    }

    @Test
    @DisplayName("findById đếm nhân viên, tài khoản; findUsage đếm đủ nhân viên, khách hàng, tài khoản, phiếu")
    void findByIdAndFindUsageCountRowsOfBranch() {
        // Chuẩn bị: chi nhánh có 1 nhân viên, 1 khách hàng, 2 tài khoản, 1 phiếu
        insertBranch("ZZ-C", "Chi nhánh test C", "0900000003");
        Long branchId = jdbcTemplate.queryForObject("SELECT id FROM app.branch WHERE code = 'ZZ-C'", Long.class);
        Long employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status) VALUES"
                        + " (CAST('0a1b2c3d-0000-4000-8000-0000000000c1' AS uuid), 'nv.test@nhdemo.local',"
                        + " 'Nhân Viên Test', ?, 'ACTIVE') RETURNING id",
                Long.class, branchId);
        Long accountTypeId = jdbcTemplate.queryForObject(
                "INSERT INTO app.account_type (code, name, dormant_after_months) VALUES ('ZZT', 'Loại test', 12)"
                        + " RETURNING id",
                Long.class);
        Long customerId = jdbcTemplate.queryForObject(
                "INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date, id_issue_place, phone,"
                        + " email, address, branch_id) VALUES ('099000000002', 'Khách Test', DATE '1990-01-01',"
                        + " DATE '2021-01-01', 'Cục Cảnh sát', '0900000001', 'khach.test@example.com', 'Địa chỉ test', ?)"
                        + " RETURNING id",
                Long.class, branchId);
        Long accountId = insertAccount("1099000011", customerId, accountTypeId, branchId);
        insertAccount("1099000012", customerId, accountTypeId, branchId);
        jdbcTemplate.update(
                "INSERT INTO app.ticket (code, type, status, account_id, branch_id, created_by, reason, submit_count,"
                        + " process_instance_id) VALUES ('PYC-2099-0001', 'PHONG_TOA', 'PENDING_APPROVAL', ?, ?, ?,"
                        + " 'Lý do test', 1, 'process-test')",
                accountId, branchId, employeeId);

        // Chạy
        Branch branch = branchMapper.findById(branchId);
        BranchUsage usage = branchMapper.findUsage(branchId);

        // Kiểm tra kết quả
        assertEquals(1L, branch.getEmployeeCount());
        assertEquals(2L, branch.getAccountCount());
        assertEquals(1L, usage.getEmployeeCount());
        assertEquals(1L, usage.getCustomerCount());
        assertEquals(2L, usage.getAccountCount());
        assertEquals(1L, usage.getTicketCount());
    }

    private void insertBranch(String code, String name, String phone) {
        jdbcTemplate.update(
                "INSERT INTO app.branch (code, name, address, phone) VALUES (?, ?, 'Địa chỉ test', ?)",
                code, name, phone);
    }

    private Long insertAccount(String accountNumber, Long customerId, Long accountTypeId, Long branchId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,"
                        + " last_transaction_at) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_DATE) RETURNING id",
                Long.class, accountNumber, customerId, accountTypeId, branchId);
    }
}