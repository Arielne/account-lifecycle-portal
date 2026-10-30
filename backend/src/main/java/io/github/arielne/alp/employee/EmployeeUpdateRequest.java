package io.github.arielne.alp.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu sửa hồ sơ, gán chi nhánh (CN-22), React gửi lên ở PUT /api/employees/{id}.
 * Không đổi trạng thái ở đây: khóa, mở khóa có API riêng.
 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        @Pattern(regexp = "\\s*\\S+(\\s+\\S+)+\\s*", message = "Họ tên phải có ít nhất 2 từ")
        String fullName,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email chưa đúng dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        Long branchId) {
}