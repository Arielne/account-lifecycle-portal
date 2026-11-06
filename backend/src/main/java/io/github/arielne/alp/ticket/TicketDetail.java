package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * JSON của GET /api/tickets/{id} (CN-15, trang 2.4): nội dung phiếu, giấy tờ đang dùng, lịch sử xử lý (mới nhất ở
 * trên). customerCccd đã che (QT-06); không có điện thoại, email của khách.
 * Cờ cho nút Duyệt, Từ chối, Sửa, Hủy thêm ở các bước sau của module phiếu.
 */
public record TicketDetail(Long id, String code, String type, String status, Long accountId, String accountNumber,
                           Long customerId, String customerName, String customerCccd, String accountTypeName,
                           String fromStatus, String toStatus, boolean newCustomer, String createdByName,
                           String branchName, String reviewedByName, String reason, String rejectReason,
                           int submitCount, OffsetDateTime createdAt, OffsetDateTime lastSubmittedAt,
                           List<Attachment> attachments, List<HistoryEntry> history) {

    /** Giấy tờ: id để gọi API tải, tên gốc, loại và dung lượng để hiện. */
    public record Attachment(Long id, String originalName, String contentType, long sizeBytes) {
    }

    /** Một bước trong lịch sử xử lý; actorName rỗng là Hệ thống. */
    public record HistoryEntry(String action, String fromStatus, String toStatus, String actorName, String note,
                               OffsetDateTime createdAt) {
    }
}