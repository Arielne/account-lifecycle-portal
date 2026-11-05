package io.github.arielne.alp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.time.OffsetDateTime;
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
 * Test chậm cho câu lọc audit log (spec admin mục 8, test 10) và câu insert: mỗi điều kiện của <where>, <if> chạy thật
 * trên Postgres của Testcontainers. Test tự thêm 4 dòng audit riêng và tự rollback sau khi chạy.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class AuditLogMapperIT {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;

    @BeforeEach
    void insertAuditRows() {
        // Chuẩn bị chung: Admin làm 3 việc ở 3 ngày khác nhau, hệ thống làm 1 việc
        adminId = insertEmployee("0a1b2c3d-0000-4000-8000-0000000000f1", "admin.audit@nhdemo.local", "Admin Audit");
        insertEmployee("0a1b2c3d-0000-4000-8000-0000000000f2", "nv.audit@nhdemo.local", "Nhân Viên Audit");
        insertAudit("2026-10-01T09:00:00+07:00", adminId, "CREATE", "BRANCH", "ZZ-A1", null);
        insertAudit("2026-10-15T09:00:00+07:00", adminId, "UPDATE", "BRANCH", "ZZ-A1",
                "[{\"label\": \"Địa chỉ\", \"before\": \"Cũ\", \"after\": \"Mới\"}]");
        insertAudit("2026-10-31T23:30:00+07:00", adminId, "LOCK", "EMPLOYEE", "nv.audit@nhdemo.local", null);
        insertAudit("2026-10-20T00:05:00+07:00", null, "UPDATE", "ACCOUNT_TYPE", "ZZT", null);
    }

    @Test
    @DisplayName("Không lọc gì: đủ 4 dòng, mới nhất ở trên; người làm rỗng là hệ thống; nhân viên là đối tượng có họ tên")
    void noFilterReturnsAllNewestFirst() {
        // Chuẩn bị
        AuditLogFilter filter = filter();

        // Chạy
        List<AuditLogRow> rows = auditLogMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("LOCK", "UPDATE", "UPDATE", "CREATE"), actionsOf(rows));
        assertEquals("Nhân Viên Audit", rows.get(0).getObjectName());
        assertEquals("Admin Audit", rows.get(0).getEmployeeName());
        assertNull(rows.get(1).getEmployeeName());
        assertEquals(4, auditLogMapper.count(filter));
    }

    @Test
    @DisplayName("Lọc theo người làm, theo hệ thống, theo hành động, theo đối tượng: mỗi điều kiện lọc đúng")
    void eachFilterSelectsMatchingRows() {
        // Chuẩn bị
        AuditLogFilter byEmployee = filter();
        byEmployee.setEmployeeId(adminId);
        AuditLogFilter bySystem = filter();
        bySystem.setSystemOnly(true);
        AuditLogFilter byAction = filter();
        byAction.setAction("UPDATE");
        AuditLogFilter byObjectType = filter();
        byObjectType.setObjectType("BRANCH");

        // Chạy, kiểm tra kết quả
        assertEquals(3, auditLogMapper.count(byEmployee));
        assertEquals(List.of("UPDATE"), actionsOf(auditLogMapper.findPage(bySystem)));
        assertEquals(2, auditLogMapper.count(byAction));
        assertEquals(List.of("UPDATE", "CREATE"), actionsOf(auditLogMapper.findPage(byObjectType)));
    }

    @Test
    @DisplayName("Lọc theo khoảng thời gian: tính cả mốc đầu, không tính mốc cuối; kết hợp được với điều kiện khác")
    void timeRangeFilterCombinesWithOtherConditions() {
        // Chuẩn bị: cả tháng 10, chỉ việc của Admin trên chi nhánh
        AuditLogFilter filter = filter();
        filter.setFromTime(OffsetDateTime.parse("2026-10-01T09:00:00+07:00"));
        filter.setToTime(OffsetDateTime.parse("2026-10-31T23:30:00+07:00"));
        filter.setEmployeeId(adminId);
        filter.setObjectType("BRANCH");

        // Chạy
        List<AuditLogRow> rows = auditLogMapper.findPage(filter);

        // Kiểm tra kết quả: dòng 01/10 09:00 được tính, dòng LOCK đúng 31/10 23:30 không tính; changes đọc ra chuỗi JSON.
        // jsonb tự sắp lại thứ tự khóa, nên chỉ kiểm từng cặp khóa và giá trị có mặt.
        assertEquals(List.of("UPDATE", "CREATE"), actionsOf(rows));
        String changesJson = rows.get(0).getChangesJson();
        assertTrue(changesJson.contains("\"label\": \"Địa chỉ\""));
        assertTrue(changesJson.contains("\"after\": \"Mới\""));
    }

    @Test
    @DisplayName("Phân trang: size và offset lấy đúng phần của trang, tổng số dòng không đổi")
    void pagingReturnsRequestedSlice() {
        // Chuẩn bị: trang 2, mỗi trang 3 dòng
        AuditLogFilter filter = filter();
        filter.setSize(3);
        filter.setOffset(3);

        // Chạy
        List<AuditLogRow> rows = auditLogMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(List.of("CREATE"), actionsOf(rows));
        assertEquals(4, auditLogMapper.count(filter));
    }

    @Test
    @DisplayName("insert: mã đối tượng là email dài hơn 50 ký tự; người làm tìm theo keycloakId, đối tượng theo email")
    void insertKeepsLongEmailObjectId() {
        // Chuẩn bị: nhân viên có email 57 ký tự, vẫn trong giới hạn 150 ký tự của employee.email
        String longEmail = "nguyen.thi.thanh.huong.phong.giao.dich.quan7@nhdemo.local";
        insertEmployee("0a1b2c3d-0000-4000-8000-0000000000f3", longEmail, "Nguyễn Thị Thanh Hương");
        AuditLogEntry entry = new AuditLogEntry();
        entry.setKeycloakId("0a1b2c3d-0000-4000-8000-0000000000f1");
        entry.setAction("CREATE");
        entry.setObjectType("EMPLOYEE");
        entry.setObjectId(longEmail);
        entry.setDescription("Tạo nhân viên");
        entry.setSource("AuditLogMapperIT");

        // Chạy
        auditLogMapper.insert(entry);

        // Kiểm tra kết quả: dữ liệu chung không có dòng CREATE nào trên EMPLOYEE, nên chỉ có dòng vừa thêm
        AuditLogFilter filter = filter();
        filter.setAction("CREATE");
        filter.setObjectType("EMPLOYEE");
        List<AuditLogRow> rows = auditLogMapper.findPage(filter);
        assertEquals(1, rows.size());
        assertEquals(longEmail, rows.get(0).getObjectId());
        assertEquals("Nguyễn Thị Thanh Hương", rows.get(0).getObjectName());
        assertEquals("Admin Audit", rows.get(0).getEmployeeName());
    }

    @Test
    @DisplayName("Nhân viên là đối tượng đổi email sau đó: dòng cũ vẫn hiện họ tên, object_id giữ email lúc thao tác")
    void objectNameSurvivesEmailChange() {
        // Chuẩn bị: nhân viên của dòng LOCK đổi email (Admin sửa, hoặc đổi trên KeyCloak rồi đăng nhập lại)
        jdbcTemplate.update("UPDATE app.employee SET email = 'nv.audit.moi@nhdemo.local'"
                + " WHERE email = 'nv.audit@nhdemo.local'");
        AuditLogFilter filter = filter();
        filter.setAction("LOCK");

        // Chạy
        List<AuditLogRow> rows = auditLogMapper.findPage(filter);

        // Kiểm tra kết quả
        assertEquals(1, rows.size());
        assertEquals("nv.audit@nhdemo.local", rows.get(0).getObjectId());
        assertEquals("Nhân Viên Audit", rows.get(0).getObjectName());
    }

    /** Bộ lọc trống: không điều kiện nào, trang đầu 10 dòng. */
    private AuditLogFilter filter() {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setSize(10);
        return filter;
    }

    private List<String> actionsOf(List<AuditLogRow> rows) {
        List<String> actions = new ArrayList<>();
        for (AuditLogRow row : rows) {
            actions.add(row.getAction());
        }
        return actions;
    }

    private Long insertEmployee(String keycloakId, String email, String fullName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app.employee (keycloak_id, email, full_name, status)"
                        + " VALUES (CAST(? AS uuid), ?, ?, 'ACTIVE') RETURNING id",
                Long.class, keycloakId, email, fullName);
    }

    /** Đối tượng là nhân viên thì ghi object_employee_id theo email, như câu INSERT của AuditLogMapper. */
    private void insertAudit(String occurredAt, Long employeeId, String action, String objectType, String objectId,
                             String changesJson) {
        jdbcTemplate.update(
                "INSERT INTO app.audit_log (occurred_at, employee_id, action, object_type, object_id,"
                        + " object_employee_id, description, changes) VALUES (CAST(? AS timestamptz), ?, ?, ?, ?,"
                        + " (SELECT id FROM app.employee WHERE ? = 'EMPLOYEE' AND email = ?), 'Mô tả test',"
                        + " CAST(? AS jsonb))",
                occurredAt, employeeId, action, objectType, objectId, objectType, objectId, changesJson);
    }
}