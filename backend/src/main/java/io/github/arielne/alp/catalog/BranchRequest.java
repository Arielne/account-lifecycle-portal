package io.github.arielne.alp.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm và sửa chi nhánh (CN-21), React gửi lên ở POST /api/branches và PUT /api/branches/{id}.
 * Mã chi nhánh sửa được, nên thêm và sửa dùng chung một DTO. Điện thoại chỉ gồm chữ số (React bỏ dấu cách trước khi gửi).
 */
public record BranchRequest(
        @NotBlank(message = "Vui lòng nhập mã chi nhánh")
        @Pattern(regexp = "[A-Z0-9-]{2,10}", message = "Mã gồm 2–10 ký tự in hoa, số hoặc dấu -")
        String code,

        @NotBlank(message = "Vui lòng nhập tên chi nhánh")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String name,

        @NotBlank(message = "Vui lòng nhập địa chỉ")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "[0-9]{10,11}", message = "Số điện thoại phải có 10–11 chữ số")
        String phone) {
}