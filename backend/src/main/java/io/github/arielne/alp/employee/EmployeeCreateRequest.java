package io.github.arielne.alp.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Dữ liệu thêm nhân viên (CN-22), React gửi lên ở POST /api/employees.
 * Chi nhánh không bắt buộc ở đây vì Admin không cần chi nhánh; Service kiểm tra MAKER, CHECKER phải có chi nhánh.
 * List<@Pattern ...>: annotation đặt trước kiểu phần tử thì Bean Validation kiểm tra từng role trong danh sách.
 */
public record EmployeeCreateRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        @Pattern(regexp = "\\s*\\S+(\\s+\\S+)+\\s*", message = "Họ tên phải có ít nhất 2 từ")
        String fullName,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email chưa đúng dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        Long branchId,

        @NotEmpty(message = "Chọn ít nhất một role")
        List<@Pattern(regexp = "MAKER|CHECKER|ADMIN", message = "Role không hợp lệ") String> roles) {
}