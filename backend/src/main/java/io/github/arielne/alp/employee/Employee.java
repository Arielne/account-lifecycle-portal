package io.github.arielne.alp.employee;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Hồ sơ nhân viên (bảng app.employee), kèm mã và tên chi nhánh đọc bằng JOIN app.branch.
 * Dùng cho GET /api/me (CN-01 đến CN-05); màn Nhân viên (CN-22) dùng tiếp ở tuần 4.
 * keycloakId để kiểu String cho đơn giản: Postgres lưu kiểu uuid, câu SQL tự ép kiểu bằng CAST(... AS uuid).
 * Role không có ở đây vì role chỉ lưu ở KeyCloak.
 */
@Getter
@Setter
public class Employee {

    private Long id;
    private String keycloakId;
    private String email;
    private String fullName;
    // 3 cột chi nhánh rỗng khi nhân viên chưa được Admin gán chi nhánh.
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String status;
    // Lúc hồ sơ được tạo: màn Nhân viên ghi "Tự đăng ký lúc ..." cho người Chờ cấp quyền.
    private OffsetDateTime createdAt;
}