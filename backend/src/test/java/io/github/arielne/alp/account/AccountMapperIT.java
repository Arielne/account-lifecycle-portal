package io.github.arielne.alp.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test chậm cho câu đọc tài khoản (spec khách hàng tài khoản mục 7, test 9 đến 11): mỗi điều kiện lọc, khớp đầu số,
 * phiếu đang mở (QT-03), xem mọi chi nhánh (QT-04), chạy thật trên Postgres của Testcontainers.
 * Test tự thêm dữ liệu riêng (số tài khoản bắt đầu bằng 1099, không trùng dữ liệu mẫu) và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class AccountMapperIT {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long branchA;
    private Long branchB;
    private Long firstCustomer;
    private Long activeAccount;
    private Long dormantAccount;

    @BeforeEach
    void insertAccounts() {
        // Chuẩn bị chung: 2 chi nhánh, 2 khách, 4 tài khoản tạo lần lượt từng ngày;
        // tài khoản Hoạt động có phiếu Phong tỏa đang chờ duyệt, tài khoản Ngủ đông có phiếu đã Hoàn tất.
        branchA = insertBranch("ZZ-A", "Chi nhánh Thử A");
        branchB = insertBranch("ZZ-B", "Chi nhánh Thử B");
        Long accountType = jdbcTemplate.queryForObject(
                "INSERT INTO app.account_type (code, name, dormant_after_months)"
                        + " VALUES ('ZZTK', 'Loại thử', 12) RETURNING id", Long.class);
        firstCustomer = insertCustomer("099000000001", "Phạm Văn Thử", branchA);
        Long secondCustomer = insertCustomer("099000000002", "Đỗ Thị Thử", branchB);
        activeAccount = insertAccount("1099000001", firstCustomer, accountType, branchA, "ACTIVE", "2026-10-01");
        dormantAccount = insertAccount("1099000002", firstCustomer, accountType, branchB, "DORMANT", "2026-10-02");
        insertAccount("1099000003", secondCustomer, accountType, branchA, "CLOSED", "2026-10-03");
        insertAccount("1099000104", secondCustomer, accountType, branchB, "BLOCKED", "2026-10-04");
        Long maker = jdbcTemplate.queryForObject(
                "INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)"
                        + " VALUES (CAST('0a1b2c3d-0000-4000-8000-0000000000c1' AS uuid), 'gdv.thu@nhdemo.local',"
                        + " 'Giao Dịch Viên Thử', ?, 'ACTIVE') RETURNING id", Long.class, branchA);
        insertTicket("PYC-2099-0001", activeAccount, branchA, maker, "PENDING_APPROVAL");
        insertTicket("PYC-2099-0002", dormantAccount, branchA, maker, "COMPLETED");
    }

    @Test
    @DisplayName("Không lọc gì ngoài đầu số 1099: đủ 4 tài khoản của cả 2 chi nhánh, mới tạo ở trên (QT-04)")
    void listsAccountsOfEveryBranchNewestFirst() {
        // Chuẩn bị
        AccountFilter filter = filter();
        filter.setAccountNumberPrefix("1099");

        // Chạy
        List<AccountRow> rows = accountMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("1099000104", "1099000003", "1099000002", "1099000001"), accountNumbersOf(rows));
        assertEquals(4, accountMapper.count(filter));
    }

    @Test
    @DisplayName("Lọc nhiều trạng thái cùng chi nhánh: chỉ ra tài khoản khớp cả hai điều kiện")
    void filtersByStatusesAndBranch() {
        // Chuẩn bị: Hoạt động hoặc Ngủ đông, ở chi nhánh A
        AccountFilter filter = filter();
        filter.setStatuses(new String[] {"ACTIVE", "DORMANT"});
        filter.setBranchId(branchA);

        // Chạy
        List<AccountRow> rows = accountMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("1099000001"), accountNumbersOf(rows));
        assertEquals(1, accountMapper.count(filter));
    }

    @Test
    @DisplayName("Tìm theo đầu số: 10990001 chỉ ra 1099000104, không ra tài khoản có số đó ở giữa")
    void matchesAccountNumberFromTheStart() {
        // Chuẩn bị
        AccountFilter filter = filter();
        filter.setAccountNumberPrefix("10990001");

        // Chạy
        List<AccountRow> rows = accountMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("1099000104"), accountNumbersOf(rows));
    }

    @Test
    @DisplayName("Lọc theo khách hàng: đủ tài khoản của khách đó; có họ tên, CCCD, tên loại và chi nhánh")
    void filtersByCustomerWithJoinedNames() {
        // Chuẩn bị
        AccountFilter filter = filter();
        filter.setCustomerId(firstCustomer);

        // Chạy
        List<AccountRow> rows = accountMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("1099000002", "1099000001"), accountNumbersOf(rows));
        AccountRow dormant = rows.get(0);
        assertEquals("Phạm Văn Thử", dormant.getCustomerName());
        assertEquals("099000000001", dormant.getCustomerCccd());
        assertEquals("Loại thử", dormant.getAccountTypeName());
        assertEquals("Chi nhánh Thử B", dormant.getBranchName());
    }

    @Test
    @DisplayName("Phiếu Chờ duyệt hiện mã, loại, trạng thái; phiếu đã Hoàn tất thì không tính là phiếu đang mở (QT-03)")
    void showsOnlyOpenTicket() {
        // Chuẩn bị: dữ liệu ở insertAccounts

        // Chạy
        AccountRow active = accountMapper.findById(activeAccount);
        AccountRow dormant = accountMapper.findById(dormantAccount);

        // Kiểm tra kết quả
        assertEquals("PYC-2099-0001", active.getOpenTicketCode());
        assertEquals("PHONG_TOA", active.getOpenTicketType());
        assertEquals("PENDING_APPROVAL", active.getOpenTicketStatus());
        assertNull(dormant.getOpenTicketCode());
    }

    @Test
    @DisplayName("Chi tiết có ngày mở, ngày giao dịch gần nhất; id không có thì trả null")
    void findByIdReadsDatesAndReturnsNullWhenMissing() {
        // Chuẩn bị: dữ liệu ở insertAccounts

        // Chạy
        AccountRow active = accountMapper.findById(activeAccount);
        AccountRow missing = accountMapper.findById(-1L);

        // Kiểm tra kết quả
        assertEquals(LocalDate.of(2024, 1, 10), active.getOpenedAt());
        assertEquals(LocalDate.of(2026, 9, 30), active.getLastTransactionAt());
        assertNull(active.getClosedAt());
        assertNull(missing);
    }

    private AccountFilter filter() {
        AccountFilter filter = new AccountFilter();
        filter.setSize(10);
        return filter;
    }

    private List<String> accountNumbersOf(List<AccountRow> rows) {
        List<String> numbers = new ArrayList<>();
        for (AccountRow row : rows) {
            numbers.add(row.getAccountNumber());
        }
        return numbers;
    }

    private Long insertBranch(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone) VALUES (?, ?, 'Địa chỉ thử', '02800000000')"
                        + " RETURNING id", Long.class, code, name);
    }

    private Long insertCustomer(String cccd, String fullName, Long branchId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.customer (cccd, full_name, date_of_birth, id_issue_date, id_issue_place, phone,"
                        + " email, address, branch_id)"
                        + " VALUES (?, ?, DATE '1990-01-01', DATE '2022-01-01', 'Nơi cấp thử', '0900000099',"
                        + " 'khach.thu@nhdemo.local', 'Địa chỉ thử', ?) RETURNING id",
                Long.class, cccd, fullName, branchId);
    }

    /** createdDate quyết định thứ tự "mới tạo ở trên"; tài khoản đã đóng có ngày đóng. */
    private Long insertAccount(String accountNumber, Long customerId, Long accountTypeId, Long branchId,
                               String status, String createdDate) {
        String closedAt = null;
        if ("CLOSED".equals(status)) {
            closedAt = "2026-10-20";
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,"
                        + " opened_at, closed_at, last_transaction_at, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, DATE '2024-01-10', CAST(? AS date), DATE '2026-09-30',"
                        + " CAST(? AS timestamptz)) RETURNING id",
                Long.class, accountNumber, customerId, accountTypeId, branchId, status, closedAt,
                createdDate + "T09:00:00+07:00");
    }

    private void insertTicket(String code, Long accountId, Long branchId, Long createdBy, String status) {
        jdbcTemplate.update(
                "INSERT INTO app.ticket (code, type, status, account_id, branch_id, created_by, reason, submit_count,"
                        + " process_instance_id) VALUES (?, 'PHONG_TOA', ?, ?, ?, ?, 'Lý do thử', 1, 'quy-trinh-thu')",
                code, status, accountId, branchId, createdBy);
    }
}