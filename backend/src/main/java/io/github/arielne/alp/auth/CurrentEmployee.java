package io.github.arielne.alp.auth;

/**
 * Người đang đăng nhập và hồ sơ nhân viên của họ. Module nghiệp vụ dùng id để ghi "người làm", và branchId làm
 * chi nhánh của thao tác: chi nhánh luôn lấy từ hồ sơ trong database, không nhận từ request (QT-04).
 * branchId, branchName rỗng khi nhân viên chưa được gán chi nhánh (ví dụ Admin).
 */
public record CurrentEmployee(Long id, String keycloakId, String fullName, Long branchId, String branchName) {
}