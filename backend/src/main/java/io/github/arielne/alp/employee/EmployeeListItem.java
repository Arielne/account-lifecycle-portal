package io.github.arielne.alp.employee;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Một dòng của GET /api/employees (CN-22): hồ sơ trong database ghép với role lấy từ KeyCloak.
 * status là ACTIVE hoặc LOCKED; người chưa có role nào là Chờ cấp quyền (React tự suy ra từ roles rỗng).
 */
public record EmployeeListItem(Long id, String keycloakId, String fullName, String email, Long branchId,
                               String branchName, List<String> roles, String status, OffsetDateTime createdAt) {
}