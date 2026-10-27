package io.github.arielne.alp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test chậm cho các script Flyway trong db/migrations, chạy trên Postgres của Testcontainers.
 * Kiểm tra Flyway tạo đủ 12 bảng theo ERD, và 2 lớp chặn nằm ở database:
 * CHECK của cột trạng thái và index UNIQUE có điều kiện của QT-03.
 * @Transactional: mỗi test chạy trong 1 transaction và tự rollback, nên dữ liệu test không ảnh hưởng nhau.
 * Dữ liệu test là dữ liệu giả; CCCD bắt đầu bằng 099 là mã tỉnh không tồn tại (QT-12).
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class DatabaseMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway tạo đủ 12 bảng của app trong schema app")
    void flywayCreatesAllTwelveTablesInAppSchema() {
        // Chuẩn bị: 12 bảng theo ERD, xếp theo thứ tự chữ cái
        List<String> expectedTables = List.of(
                "account", "account_type", "attachment", "audit_log", "branch", "customer",
                "employee", "import_batch", "import_batch_row", "outbox_event", "ticket", "ticket_history");

        // Chạy
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'app' AND table_name <> 'flyway_schema_history'"
                        + " ORDER BY table_name",
                String.class);

        // Kiểm tra kết quả
        assertEquals(expectedTables, actualTables);
    }

    @Test
    @DisplayName("Cột account.status không nhận giá trị ngoài 5 trạng thái")
    void accountStatusRejectsValueOutsideFiveStatuses() {
        // Chuẩn bị
        long branchId = createBranch();
        long customerId = createCustomer(branchId);
        long accountTypeId = createAccountType();

        // Chạy và kiểm tra kết quả: CHECK của database chặn trạng thái gõ sai
        assertThrows(DataIntegrityViolationException.class,
                () -> createAccount(customerId, accountTypeId, branchId, "OPEN"));
    }

    @Test
    @DisplayName("QT-03: một tài khoản không có được 2 phiếu đang Chờ duyệt hoặc Bị từ chối")
    void accountCannotHaveTwoOpenTickets() {
        // Chuẩn bị: 1 tài khoản đã có 1 phiếu đang Chờ duyệt
        long branchId = createBranch();
        long accountId = createAccount(createCustomer(branchId), createAccountType(), branchId, "ACTIVE");
        long employeeId = createEmployee(branchId);
        createTicket("PYC-TEST-0001", accountId, branchId, employeeId, "PENDING_APPROVAL");

        // Chạy và kiểm tra kết quả: phiếu thứ 2 đang mở bị chặn
        assertThrows(DuplicateKeyException.class,
                () -> createTicket("PYC-TEST-0002", accountId, branchId, employeeId, "REJECTED"));
    }

    @Test
    @DisplayName("QT-03: phiếu cũ đã hoàn tất thì tài khoản được tạo phiếu mới")
    void accountCanHaveNewTicketAfterOldTicketCompleted() {
        // Chuẩn bị: 1 tài khoản có 1 phiếu đã hoàn tất
        long branchId = createBranch();
        long accountId = createAccount(createCustomer(branchId), createAccountType(), branchId, "ACTIVE");
        long employeeId = createEmployee(branchId);
        createTicket("PYC-TEST-0003", accountId, branchId, employeeId, "COMPLETED");

        // Chạy và kiểm tra kết quả: index chỉ xét phiếu đang mở nên phiếu mới được tạo
        assertDoesNotThrow(
                () -> createTicket("PYC-TEST-0004", accountId, branchId, employeeId, "PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Dòng import bị lỗi, thiếu dữ liệu từ Excel, vẫn lưu được kèm lý do lỗi")
    void importErrorRowWithMissingDataCanBeSaved() {
        // Chuẩn bị: 1 lô import do nhân viên tạo
        long importBatchId = createImportBatch(createEmployee(createBranch()));

        // Chạy và kiểm tra kết quả: dòng ERROR chỉ có số dòng, kết quả và lý do, các ô dữ liệu để rỗng
        assertDoesNotThrow(() -> jdbcTemplate.update(
                "INSERT INTO app.import_batch_row (import_batch_id, row_number, result, error_reason)"
                        + " VALUES (?, 2, 'ERROR', 'Thiếu ngày sinh và CCCD')",
                importBatchId));
    }

    private long createImportBatch(long employeeId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.import_batch (file_name, storage_key, status, total_rows, valid_rows,"
                        + " error_rows, created_by)"
                        + " VALUES ('lo-test.xlsx', 'storage-key-test', 'CHECKED', 1, 0, 1, ?) RETURNING id",
                Long.class, employeeId);
    }

    private long createBranch() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone)"
                        + " VALUES ('TST01', 'Chi nhánh test', 'Địa chỉ test', '0900000000') RETURNING id",
                Long.class);
    }

    private long createAccountType() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.account_type (code, name, dormant_after_months)"
                        + " VALUES ('TST', 'Loại test', 12) RETURNING id",
                Long.class);
    }

    private long createCustomer(long branchId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date, id_issue_place,"
                        + " phone, email, address, branch_id)"
                        + " VALUES ('099000000001', 'Khách Test', '1990-01-01', '2021-01-01', 'Nơi cấp test',"
                        + " '0900000001', 'khach.test@nhdemo.local', 'Địa chỉ test', ?) RETURNING id",
                Long.class, branchId);
    }

    private long createAccount(long customerId, long accountTypeId, long branchId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,"
                        + " last_transaction_at)"
                        + " VALUES ('1020009999', ?, ?, ?, ?, CURRENT_DATE) RETURNING id",
                Long.class, customerId, accountTypeId, branchId, status);
    }

    private long createEmployee(long branchId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                        + " VALUES (gen_random_uuid(), 'nhanvien.test@nhdemo.local', 'Nhân viên Test', ?, 'ACTIVE')"
                        + " RETURNING id",
                Long.class, branchId);
    }

    private void createTicket(String code, long accountId, long branchId, long employeeId, String status) {
        jdbcTemplate.update(
                "INSERT INTO app.ticket (code, type, status, account_id, branch_id, created_by, reason,"
                        + " submit_count, process_instance_id)"
                        + " VALUES (?, 'PHONG_TOA', ?, ?, ?, ?, 'Lý do test', 1, 'process-test')",
                code, status, accountId, branchId, employeeId);
    }
}