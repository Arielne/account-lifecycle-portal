package io.github.arielne.alp.ticket;

import io.github.arielne.alp.customer.CustomerProfile;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Hồ sơ khách mới trong phiếu Mở tài khoản (CN-11, spec phiếu mục 7.1). Quy tắc giống Form ở màn Tạo phiếu.
 * gender: "Nam", "Nữ" hoặc rỗng. Họ tên, nơi cấp, địa chỉ còn được chuẩn hóa ở customer trước khi lưu.
 */
public record NewCustomerRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự")
        @Pattern(regexp = "\\s*\\S+(\\s+\\S+)+\\s*", message = "Họ tên phải có ít nhất 2 từ")
        String fullName,

        @NotNull(message = "Vui lòng nhập ngày sinh")
        @Past(message = "Ngày sinh phải ở quá khứ")
        LocalDate dateOfBirth,

        @Pattern(regexp = "(Nam|Nữ)?", message = "Giới tính là Nam hoặc Nữ")
        String gender,

        @NotNull(message = "Vui lòng nhập số CCCD")
        @Pattern(regexp = "\\d{12}", message = "Số CCCD phải đủ 12 chữ số")
        String cccd,

        @NotNull(message = "Vui lòng nhập ngày cấp")
        @PastOrPresent(message = "Ngày cấp không được sau hôm nay")
        LocalDate idIssueDate,

        @NotBlank(message = "Vui lòng nhập nơi cấp")
        @Size(max = 100, message = "Nơi cấp tối đa 100 ký tự")
        String idIssuePlace,

        @NotNull(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "\\d{10,11}", message = "Số điện thoại gồm 10 đến 11 chữ số")
        String phone,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email chưa đúng dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotBlank(message = "Vui lòng nhập địa chỉ thường trú")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address) {

    /** Ngày cấp CCCD không trước ngày sinh. Thiếu một trong hai thì để @NotNull báo. */
    @AssertTrue(message = "Ngày cấp không được trước ngày sinh")
    public boolean isIssueDateAfterBirth() {
        if (dateOfBirth == null || idIssueDate == null) {
            return true;
        }
        return !idIssueDate.isBefore(dateOfBirth);
    }

    public CustomerProfile toProfile() {
        return new CustomerProfile(fullName, dateOfBirth, gender, cccd, idIssueDate, idIssuePlace, phone, email,
                address);
    }
}