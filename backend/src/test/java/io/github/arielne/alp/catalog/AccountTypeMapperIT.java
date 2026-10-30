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
 * Test chậm cho AccountTypeMapper (spec admin mục 8, test 10): câu đếm số tài khoản đang dùng mỗi loại,
 * chạy thật trên Postgres của Testcontainers. Test tự thêm dữ liệu riêng và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class AccountTypeMapperIT {

    @Autowired
    private AccountTypeMapper accountTypeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("findAll đếm đúng số tài khoản của từng loại, loại chưa ai dùng đếm 0")
    void findAllCountsAccountsOfEachType() {
        // Chuẩn bị: loại ZZA có 2 tài khoản, loại ZZB chưa có tài khoản nào
        Long branchId = insertBranch();
        Long usedTypeId = insertAccountType("ZZA");
        insertAccountType("ZZB");
        Long customerId = insertCustomer(branchId);
        insertAccount("1099000001", customerId, usedTypeId, branchId);
        insertAccount("1099000002", customerId, usedTypeId, branchId);

        // Chạy
        List<AccountType> accountTypes = accountTypeMapper.findAll();

        // Kiểm tra kết quả: xếp theo mã nên ZZA đứng trước ZZB
        assertEquals(2, accountTypes.size());
        assertEquals("ZZA", accountTypes.get(0).getCode());
        assertEquals(2L, accountTypes.get(0).getAccountCount());
        assertEquals(0L, accountTypes.get(1).getAccountCount());
    }

    @Test
    @DisplayName("insert gán id mới vào object; update đổi tên và số tháng nhưng không đổi mã")
    void insertThenUpdateKeepsCode() {
        // Chuẩn bị
        AccountType accountType = new AccountType();
        accountType.setCode("ZZC");
        accountType.setName("Tên cũ");
        accountType.setDormantAfterMonths(18);
        accountTypeMapper.insert(accountType);

        // Chạy: object gửi vào update mang mã khác, câu UPDATE không đụng cột code
        accountType.setCode("ZZD");
        accountType.setName("Tên mới");
        accountType.setDormantAfterMonths(12);
        accountTypeMapper.update(accountType);

        // Kiểm tra kết quả
        assertNotNull(accountType.getId());
        AccountType saved = accountTypeMapper.findById(accountType.getId());
        assertEquals("ZZC", saved.getCode());
        assertEquals("Tên mới", saved.getName());
        assertEquals(12, saved.getDormantAfterMonths());
    }

    private Long insertBranch() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone)"
                        + " VALUES ('ZZ-LTK', 'Chi nhánh test', 'Địa chỉ test', '0900000000') RETURNING id",
                Long.class);
    }

    private Long insertAccountType(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.account_type (code, name, dormant_after_months) VALUES (?, 'Loại test', 12) RETURNING id",
                Long.class, code);
    }

    private Long insertCustomer(Long branchId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date, id_issue_place, phone,"
                        + " email, address, branch_id) VALUES ('099000000001', 'Khách Test', DATE '1990-01-01',"
                        + " DATE '2021-01-01', 'Cục Cảnh sát', '0900000001', 'khach.test@example.com', 'Địa chỉ test', ?)"
                        + " RETURNING id",
                Long.class, branchId);
    }

    private void insertAccount(String accountNumber, Long customerId, Long accountTypeId, Long branchId) {
        jdbcTemplate.update(
                "INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,"
                        + " last_transaction_at) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_DATE)",
                accountNumber, customerId, accountTypeId, branchId);
    }
}