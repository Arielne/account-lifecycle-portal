package io.github.arielne.alp.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu sửa loại tài khoản (CN-20), React gửi lên ở PUT /api/account-types/{id}.
 * Không có ô mã: mã loại không sửa được sau khi tạo; JSON gửi kèm mã thì Spring bỏ qua.
 */
public record AccountTypeUpdateRequest(
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