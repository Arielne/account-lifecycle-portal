package io.github.arielne.alp.employee;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Danh sách role mới của nhân viên (CN-22), React gửi lên ở PUT /api/employees/{id}/roles.
 * Danh sách này thay cho danh sách cũ: Service so với role hiện có trên KeyCloak rồi thêm hoặc bỏ từng role.
 */
public record EmployeeRolesRequest(
        @NotEmpty(message = "Chọn ít nhất một role")
        List<@Pattern(regexp = "MAKER|CHECKER|ADMIN", message = "Role không hợp lệ") String> roles) {
}