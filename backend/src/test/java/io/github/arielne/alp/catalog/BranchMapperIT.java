package io.github.arielne.alp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
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
@Import(PostgresTestcontainersConfiguration.class)
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

    private void insertBranch(String code, String name, String phone) {
        jdbcTemplate.update(
                "INSERT INTO app.branch (code, name, address, phone) VALUES (?, ?, 'Địa chỉ test', ?)",
                code, name, phone);
    }
}