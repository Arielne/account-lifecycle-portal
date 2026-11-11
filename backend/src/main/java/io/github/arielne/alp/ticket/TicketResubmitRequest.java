package io.github.arielne.alp.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Phần "data" (JSON) của PUT /api/tickets/{id}: sửa phiếu Bị từ chối rồi gửi lại (CN-12, spec phiếu mục 7.3).
 * Không đổi loại phiếu, tài khoản, khách hàng: muốn đổi thì hủy phiếu và tạo phiếu mới.
 * - accountTypeId: chỉ phiếu Mở tài khoản, bắt buộc với loại phiếu này.
 * - newCustomer: chỉ phiếu Mở tài khoản đã tạo hồ sơ khách mới (D2).
 * - removedAttachmentIds: giấy tờ cũ cần bỏ (D3); giấy tờ mới gửi ở phần "files".
 */
public record TicketResubmitRequest(
        Long accountTypeId,

        @Valid
        NewCustomerRequest newCustomer,

        @NotBlank(message = "Vui lòng nhập lý do")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        List<Long> removedAttachmentIds,

        @NotNull(message = "Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc")
        @AssertTrue(message = "Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc")
        Boolean confirmed) {

    /** Danh sách giấy tờ cần bỏ, không gửi thì là danh sách rỗng. */
    public List<Long> removedAttachmentIdsOrEmpty() {
        if (removedAttachmentIds == null) {
            return List.of();
        }
        return removedAttachmentIds;
    }
}