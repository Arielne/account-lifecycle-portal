package io.github.arielne.alp.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu thêm loại tài khoản (CN-20), React gửi lên ở POST /api/account-types.
 * Bean Validation: @Valid ở Controller kiểm tra các annotation dưới đây trước khi gọi Service;
 * sai thì trả 400 kèm câu báo lỗi của từng ô (spec admin mục 3, mục 5).
 */
public record AccountTypeCreateRequest(
        @NotBlank(message = "Vui lòng nhập mã loại")
        @Pattern(regexp = "[A-Z0-9]{2,10}", message = "Mã gồm 2–10 ký tự in hoa hoặc số")
        String code,

        @NotBlank(message = "Vui lòng nhập tên loại tài khoản")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String name,

        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description,

        @NotNull(message = "Vui lòng nhập số tháng")
        @Min(value = 1, message = "Số tháng từ 1 đến 120")
        @Max(value = 120, message = "Số tháng từ 1 đến 120")
        Integer dormantAfterMonths) {
}