package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * JSON của GET /api/tickets/{id} (CN-15, trang 2.4): nội dung phiếu, giấy tờ đang dùng, lịch sử xử lý (mới nhất ở
 * trên). customerCccd đã che (QT-06); không có điện thoại, email của khách.
 * Cờ cho nút, tính cho người đang xem (spec phiếu mục 8.3): canApprove, canReject là được bấm Duyệt, Từ chối;
 * blockedReason có giá trị khi nút hiện nhưng bị khóa (người tạo phiếu cũng là kiểm soát viên, QT-01). Kiểm soát
 * viên khác chi nhánh thì cả hai cờ false và blockedReason rỗng: không thấy nút (D11). Cờ Sửa, Hủy thêm ở bước sau.
 */
public record TicketDetail(Long id, String code, String type, String status, Long accountId, String accountNumber,
                           Long customerId, String customerName, String customerCccd, String accountTypeName,
                           String fromStatus, String toStatus, boolean newCustomer, String createdByName,
                           String branchName, String reviewedByName, String reason, String rejectReason,
                           int submitCount, OffsetDateTime createdAt, OffsetDateTime lastSubmittedAt,
                           List<Attachment> attachments, List<HistoryEntry> history,
                           boolean canApprove, boolean canReject, String blockedReason) {

    /** Giấy tờ: id để gọi API tải, tên gốc, loại và dung lượng để hiện. */
    public record Attachment(Long id, String originalName, String contentType, long sizeBytes) {
    }

    /** Một bước trong lịch sử xử lý; actorName rỗng là Hệ thống. */
    public record HistoryEntry(String action, String fromStatus, String toStatus, String actorName, String note,
                               OffsetDateTime createdAt) {
    }
}