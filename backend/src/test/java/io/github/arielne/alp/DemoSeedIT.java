package io.github.arielne.alp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
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
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
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

    @Test
    @DisplayName("Vòng đời mẫu: mỗi tài khoản 1 mục mở, tài khoản Ngủ đông và Đã đóng thêm 1 mục; chạy lại không thêm trùng")
    void demoDataHasAccountLifecycle() throws Exception {
        // Chuẩn bị: số tài khoản Ngủ đông, Đã đóng trong dữ liệu mẫu
        int dormantCount = countRows("SELECT count(*) FROM app.account WHERE status = 'DORMANT'");
        int closedCount = countRows("SELECT count(*) FROM app.account WHERE status = 'CLOSED'");
        String seedFile = Files.readString(Path.of("../db/seeds/R__3_khach_hang_tai_khoan.sql"));

        // Chạy: chạy lại cả file seed như khi Flyway chạy lại seed đã sửa
        jdbcTemplate.execute(seedFile);

        // Kiểm tra kết quả: các dòng đều không có phiếu, không có người làm (Hệ thống)
        assertEquals(60, countRows("SELECT count(*) FROM app.account_status_history WHERE from_status IS NULL"));
        assertEquals(dormantCount, countRows("SELECT count(*) FROM app.account_status_history"
                + " WHERE to_status = 'DORMANT'"));
        assertEquals(closedCount, countRows("SELECT count(*) FROM app.account_status_history"
                + " WHERE to_status = 'CLOSED'"));
        assertEquals(0, countRows("SELECT count(*) FROM app.account_status_history"
                + " WHERE ticket_id IS NOT NULL OR changed_by IS NOT NULL OR changed_at > now()"));
    }

    private int countRows(String countQuery) {
        return jdbcTemplate.queryForObject(countQuery, Integer.class);
    }
}