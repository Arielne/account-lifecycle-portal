package io.github.arielne.alp.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
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
 * Test chậm cho câu tìm khách hàng (spec khách hàng tài khoản mục 7, test 6 đến 8 và 11): hàm app.unaccent_vi (V11),
 * tìm họ tên không dấu, tìm CCCD khớp tuyệt đối, phân trang theo khách kèm trạng thái tài khoản, xem mọi chi nhánh
 * (QT-04), chạy thật trên Postgres của Testcontainers. Test tự thêm dữ liệu riêng và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class CustomerMapperIT {

    // Đủ 67 nguyên âm có dấu và chữ đ, chữ thường rồi chữ hoa; mỗi chữ đổi thành chữ không dấu cùng vị trí.
    private static final String ACCENTED_LETTERS =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
                    + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ";
    private static final String PLAIN_LETTERS =
            "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd"
                    + "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd";

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long binh;
    private Long an;

    @BeforeEach
    void insertCustomers() {
        // Chuẩn bị chung: 2 chi nhánh; Đỗ Thị Bình ở chi nhánh A, Trần Văn An ở chi nhánh B;
        // 12 khách "Khách Trang" tạo lần lượt từng ngày, mỗi khách 2 tài khoản.
        Long branchA = insertBranch("ZZ-KA", "Chi nhánh Khách A");
        Long branchB = insertBranch("ZZ-KB", "Chi nhánh Khách B");
        Long accountType = jdbcTemplate.queryForObject(
                "INSERT INTO app.account_type (code, name, dormant_after_months)"
                        + " VALUES ('ZZKH', 'Loại thử', 12) RETURNING id", Long.class);
        binh = insertCustomer("099000000101", "Đỗ Thị Bình", branchA, "2026-09-01");
        an = insertCustomer("099000000102", "Trần Văn An", branchB, "2026-09-02");
        for (int number = 1; number <= 12; number++) {
            String suffix = String.format("%02d", number);
            Long customer = insertCustomer("0990000002" + suffix, "Khách Trang " + suffix, branchA,
                    "2026-10-" + suffix);
            insertAccount("10990002" + suffix, customer, accountType, branchA, "ACTIVE");
            insertAccount("10990003" + suffix, customer, accountType, branchA, "DORMANT");
        }
    }

    @Test
    @DisplayName("Hàm app.unaccent_vi đổi đúng mọi nguyên âm có dấu, chữ hoa lẫn chữ thường, và chữ đ")
    void unaccentConvertsEveryVietnameseLetter() {
        // Chuẩn bị: dữ liệu ở hằng số đầu class

        // Chạy
        String converted = jdbcTemplate.queryForObject("SELECT app.unaccent_vi(?)", String.class, ACCENTED_LETTERS);
        String name = jdbcTemplate.queryForObject("SELECT app.unaccent_vi(?)", String.class, "ĐẶNG Thị Ánh NGUYỆT");

        // Kiểm tra kết quả
        assertEquals(PLAIN_LETTERS, converted);
        assertEquals("dang thi anh nguyet", name);
    }

    @Test
    @DisplayName("Tìm họ tên gần đúng: \"do thi binh\", \"BÌNH\", \"bình\" đều ra Đỗ Thị Bình (QT-04: không lọc chi nhánh)")
    void findsNameWithoutAccentsOrCase() {
        // Chuẩn bị, chạy, kiểm tra kết quả
        assertEquals(List.of(binh), idsFound("do thi binh"));
        assertEquals(List.of(binh), idsFound("BÌNH"));
        assertEquals(List.of(binh), idsFound("bình"));
        assertEquals(List.of(an), idsFound("tran van"));
    }

    @Test
    @DisplayName("Gõ % hay _ không thành ký tự đại diện: không ra khách nào")
    void percentSignIsNotAWildcard() {
        // Chuẩn bị, chạy, kiểm tra kết quả
        assertEquals(List.of(), idsFound("%"));
        assertEquals(List.of(), idsFound("_"));
    }

    @Test
    @DisplayName("Tìm CCCD đủ 12 số ra đúng 1 khách, kể cả khách ở chi nhánh khác")
    void findsCustomerByExactCccd() {
        // Chuẩn bị
        CustomerFilter filter = filter();
        filter.setCccd("099000000102");

        // Chạy
        List<CustomerRow> rows = customerMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(1, rows.size());
        assertEquals("Trần Văn An", rows.get(0).getFullName());
        assertEquals("Chi nhánh Khách B", rows.get(0).getBranchName());
        assertEquals(1, customerMapper.count(filter));
    }

    @Test
    @DisplayName("Phân trang theo khách: trang 1 đủ 10 khách dù mỗi khách 2 tài khoản; câu thứ hai trả đủ tài khoản của 10 khách")
    void pagesByCustomerNotByAccountRow() {
        // Chuẩn bị
        CustomerFilter firstPage = filter();
        firstPage.setNameKeyword("khach trang");
        CustomerFilter secondPage = filter();
        secondPage.setNameKeyword("khach trang");
        secondPage.setOffset(10);

        // Chạy
        List<CustomerRow> firstRows = customerMapper.findPage(firstPage);
        List<CustomerRow> secondRows = customerMapper.findPage(secondPage);
        List<CustomerAccountRow> accounts = customerMapper.findAccountStatuses(idsOf(firstRows).toArray(new Long[0]));

        // Kiểm tra kết quả: hồ sơ mới tạo ở trên
        assertEquals(10, firstRows.size());
        assertEquals("Khách Trang 12", firstRows.get(0).getFullName());
        assertEquals(2, secondRows.size());
        assertEquals(12, customerMapper.count(firstPage));
        assertEquals(20, accounts.size());
        assertEquals(List.of("ACTIVE", "DORMANT"), statusesOf(accounts, firstRows.get(0).getId()));
    }

    @Test
    @DisplayName("Chi tiết khách có đủ trường và tên chi nhánh; id không có thì trả null")
    void findByIdReadsAllFieldsAndReturnsNullWhenMissing() {
        // Chuẩn bị: dữ liệu ở insertCustomers

        // Chạy
        CustomerRow row = customerMapper.findById(binh);
        CustomerRow missing = customerMapper.findById(-1L);

        // Kiểm tra kết quả
        assertEquals("099000000101", row.getCccd());
        assertEquals("Nữ", row.getGender());
        assertEquals("Nơi cấp thử", row.getIdIssuePlace());
        assertEquals("khach.thu@nhdemo.local", row.getEmail());
        assertEquals("Chi nhánh Khách A", row.getBranchName());
        assertNull(missing);
    }

    private CustomerFilter filter() {
        CustomerFilter filter = new CustomerFilter();
        filter.setSize(10);
        return filter;
    }

    private List<Long> idsFound(String nameKeyword) {
        CustomerFilter filter = filter();
        filter.setNameKeyword(nameKeyword);
        return idsOf(customerMapper.findPage(filter));
    }

    private List<Long> idsOf(List<CustomerRow> rows) {
        List<Long> ids = new ArrayList<>();
        for (CustomerRow row : rows) {
            ids.add(row.getId());
        }
        return ids;
    }

    private List<String> statusesOf(List<CustomerAccountRow> accounts, Long customerId) {
        List<String> statuses = new ArrayList<>();
        for (CustomerAccountRow account : accounts) {
            if (account.getCustomerId().equals(customerId)) {
                statuses.add(account.getStatus());
            }
        }
        return statuses;
    }

    private Long insertBranch(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.branch (code, name, address, phone) VALUES (?, ?, 'Địa chỉ thử', '02800000000')"
                        + " RETURNING id", Long.class, code, name);
    }

    private Long insertCustomer(String cccd, String fullName, Long branchId, String createdDate) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.customer (cccd, full_name, date_of_birth, gender, id_issue_date, id_issue_place,"
                        + " phone, email, address, branch_id, created_at)"
                        + " VALUES (?, ?, DATE '1990-01-01', 'Nữ', DATE '2022-01-01', 'Nơi cấp thử', '0900000099',"
                        + " 'khach.thu@nhdemo.local', 'Địa chỉ thử', ?, CAST(? AS timestamptz)) RETURNING id",
                Long.class, cccd, fullName, branchId, createdDate + "T09:00:00+07:00");
    }

    private void insertAccount(String accountNumber, Long customerId, Long accountTypeId, Long branchId,
                               String status) {
        jdbcTemplate.update(
                "INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,"
                        + " opened_at, last_transaction_at) VALUES (?, ?, ?, ?, ?, DATE '2024-01-10', DATE '2026-09-30')",
                accountNumber, customerId, accountTypeId, branchId, status);
    }
}