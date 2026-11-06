package io.github.arielne.alp.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phần "data" (JSON) của POST /api/tickets (CN-11, spec phiếu mục 7.1). Không có trường chi nhánh hay người tạo:
 * backend lấy từ hồ sơ người đang đăng nhập (QT-04).
 * - Mở tài khoản: đúng một trong customerId (khách đã có) và newCustomer (khách mới), kèm accountTypeId.
 * - 3 loại còn lại: chỉ accountNumber.
 * Các method isXxx có @AssertTrue: Bean Validation gọi method đó, trả false thì thành lỗi của ô cùng tên
 * (ví dụ customerChoiceValid), dùng cho quy tắc liên quan nhiều ô.
 */
public record TicketCreateRequest(
        @NotNull(message = "Chọn loại phiếu")
        TicketType type,

        Long customerId,

        @Valid
        NewCustomerRequest newCustomer,

        Long accountTypeId,

        @Pattern(regexp = "\\d{10}", message = "Số tài khoản gồm 10 chữ số")
        String accountNumber,

        @NotBlank(message = "Vui lòng nhập lý do")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @NotNull(message = "Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc")
        @AssertTrue(message = "Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc")
        Boolean confirmed) {

    /** Phiếu lô tạo ở màn Import, không qua API này. */
    @AssertTrue(message = "Loại phiếu không hợp lệ")
    public boolean isSingleTicketType() {
        return type != TicketType.MO_TK_THEO_LO;
    }

    @AssertTrue(message = "Chọn khách hàng đã có hoặc nhập hồ sơ khách mới, chỉ một trong hai")
    public boolean isCustomerChoiceValid() {
        if (type != TicketType.MO_TAI_KHOAN) {
            return true;
        }
        boolean hasExistingCustomer = customerId != null;
        boolean hasNewCustomer = newCustomer != null;
        return hasExistingCustomer != hasNewCustomer;
    }

    @AssertTrue(message = "Chọn loại tài khoản")
    public boolean isAccountTypeChosen() {
        return type != TicketType.MO_TAI_KHOAN || accountTypeId != null;
    }

    @AssertTrue(message = "Nhập số tài khoản")
    public boolean isAccountNumberGiven() {
        return type == null || !type.needsExistingAccount() || accountNumber != null;
    }
}