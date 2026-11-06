package io.github.arielne.alp.ticket;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Xem chi tiết phiếu và tải giấy tờ (CN-15). Chỉ đọc nên không có @Audited. Giao dịch viên, kiểm soát viên xem
 * được phiếu của mọi chi nhánh (QT-04); CCCD của khách được che, không trả điện thoại, email (QT-06).
 */
@Service
@RequiredArgsConstructor
public class TicketQueryService {

    private final TicketMapper ticketMapper;
    private final TicketAttachmentService attachmentService;

    /** Chi tiết một phiếu kèm giấy tờ đang dùng và lịch sử xử lý; không có thì 404. */
    public TicketDetail findDetail(Long id) {
        TicketDetailRow row = ticketMapper.findDetail(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
        }
        return new TicketDetail(row.getId(), row.getCode(), row.getType(), row.getStatus(), row.getAccountId(),
                row.getAccountNumber(), row.getCustomerId(), row.getCustomerName(),
                PersonalDataMasker.maskCccd(row.getCustomerCccd()), row.getAccountTypeName(), row.getFromStatus(),
                row.getToStatus(), row.isNewCustomer(), row.getCreatedByName(), row.getBranchName(),
                row.getReviewedByName(), row.getReason(), row.getRejectReason(), row.getSubmitCount(),
                row.getCreatedAt(), row.getLastSubmittedAt(), attachmentsOf(id), historyOf(id));
    }

    /**
     * Nội dung một giấy tờ để tải qua backend (QT-10). Giấy tờ không có, đã bị bỏ, hoặc không thuộc đúng phiếu trên
     * URL đều trả 404, để không đoán được giấy tờ của phiếu khác.
     */
    public AttachmentContent readAttachment(Long ticketId, Long attachmentId) {
        AttachmentRow attachment = ticketMapper.findActiveAttachment(ticketId, attachmentId);
        if (attachment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy giấy tờ");
        }
        byte[] content = attachmentService.read(attachment);
        return new AttachmentContent(attachment.getOriginalName(), attachment.getContentType(), content);
    }

    private List<TicketDetail.Attachment> attachmentsOf(Long ticketId) {
        List<TicketDetail.Attachment> attachments = new ArrayList<>();
        for (AttachmentRow row : ticketMapper.findActiveAttachments(ticketId)) {
            attachments.add(new TicketDetail.Attachment(row.getId(), row.getOriginalName(), row.getContentType(),
                    row.getSizeBytes()));
        }
        return attachments;
    }

    private List<TicketDetail.HistoryEntry> historyOf(Long ticketId) {
        List<TicketDetail.HistoryEntry> history = new ArrayList<>();
        for (TicketHistoryRow row : ticketMapper.findHistory(ticketId)) {
            history.add(new TicketDetail.HistoryEntry(row.getAction(), row.getFromStatus(), row.getToStatus(),
                    row.getActorName(), row.getNote(), row.getCreatedAt()));
        }
        return history;
    }

    /** Một giấy tờ để trả cho trình duyệt: tên gốc (cho Content-Disposition), loại đã kiểm lúc lưu, nội dung. */
    public record AttachmentContent(String originalName, String contentType, byte[] content) {
    }
}