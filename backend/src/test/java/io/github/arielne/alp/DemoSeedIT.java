package io.github.arielne.alp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test chậm cho dữ liệu mẫu trong db/seeds: bật profile demo trên Postgres của Testcontainers,
 * để lỗi SQL trong file seed lộ ra ở đây chứ không phải lúc nạp lên Supabase.
 * Đây là test của chính file seed; test của các chức năng khác vẫn tự tạo dữ liệu riêng.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("demo")
class DemoSeedIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Profile demo nạp đủ danh mục, 4 nhân viên demo, 30 khách hàng và 60 tài khoản")
    void demoProfileLoadsAllSeedData() {
        // Chuẩn bị: Flyway đã chạy migration và seed khi ứng dụng khởi động với profile demo

        // Chạy
        int branchCount = countRows("SELECT count(*) FROM app.branch");
        int accountTypeCount = countRows("SELECT count(*) FROM app.account_type");
        int employeeCount = countRows("SELECT count(*) FROM app.employee");
        int customerCount = countRows("SELECT count(*) FROM app.customer");
        int accountCount = countRows("SELECT count(*) FROM app.account");

        // Kiểm tra kết quả
        assertEquals(6, branchCount);
        assertEquals(4, accountTypeCount);
        assertEquals(4, employeeCount);
        assertEquals(30, customerCount);
        assertEquals(60, accountCount);
    }

    @Test
    @DisplayName("Dữ liệu mẫu có 6 tài khoản Hoạt động đã quá hạn giao dịch để demo job Ngủ đông")
    void demoDataHasActiveAccountsReadyForDormantJob() {
        // Chuẩn bị: hạn của mỗi tài khoản là số tháng dormant_after_months của loại tài khoản

        // Chạy
        int staleActiveCount = countRows(
                "SELECT count(*) FROM app.account a JOIN app.account_type t ON t.id = a.account_type_id"
                        + " WHERE a.status = 'ACTIVE'"
                        + " AND a.last_transaction_at < CURRENT_DATE - make_interval(months => t.dormant_after_months)");

        // Kiểm tra kết quả
        assertEquals(6, staleActiveCount);
    }

    private int countRows(String countQuery) {
        return jdbcTemplate.queryForObject(countQuery, Integer.class);
    }
}