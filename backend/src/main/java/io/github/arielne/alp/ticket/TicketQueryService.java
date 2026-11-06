package io.github.arielne.alp.ticket;

import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
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
    private final CurrentEmployeeService currentEmployeeService;

    /** Chi tiết một phiếu kèm giấy tờ đang dùng, lịch sử xử lý và cờ nút cho người đang xem; không có thì 404. */
    public TicketDetail findDetail(Long id) {
        TicketDetailRow row = ticketMapper.findDetail(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
        }
        ReviewFlags review = reviewFlagsFor(row);
        return new TicketDetail(row.getId(), row.getCode(), row.getType(), row.getStatus(), row.getAccountId(),
                row.getAccountNumber(), row.getCustomerId(), row.getCustomerName(),
                PersonalDataMasker.maskCccd(row.getCustomerCccd()), row.getAccountTypeName(), row.getFromStatus(),
                row.getToStatus(), row.isNewCustomer(), row.getCreatedByName(), row.getBranchName(),
                row.getReviewedByName(), row.getReason(), row.getRejectReason(), row.getSubmitCount(),
                row.getCreatedAt(), row.getLastSubmittedAt(), attachmentsOf(id), historyOf(id),
                review.allowed(), review.allowed(), review.blockedReason());
    }

    /**
     * Cờ nút Duyệt, Từ chối (spec phiếu mục 8.3), cùng luật với TicketReviewService: chỉ kiểm soát viên, chỉ phiếu
     * Chờ duyệt. Người tạo phiếu thấy nút bị khóa kèm lý do (QT-01); kiểm soát viên khác chi nhánh không thấy nút (D11).
     */
    private ReviewFlags reviewFlagsFor(TicketDetailRow row) {
        boolean pending = TicketStatus.PENDING_APPROVAL.name().equals(row.getStatus());
        if (!pending || !currentEmployeeService.currentUserHasRole("CHECKER")) {
            return new ReviewFlags(false, null);
        }
        CurrentEmployee viewer = currentEmployeeService.get();
        if (viewer.keycloakId().equals(row.getCreatorKeycloakId())) {
            return new ReviewFlags(false, TicketReviewService.SELF_REVIEW);
        }
        if (!row.getBranchId().equals(viewer.branchId())) {
            return new ReviewFlags(false, null);
        }
        return new ReviewFlags(true, null);
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

    /** allowed: được bấm Duyệt, Từ chối; blockedReason: nút hiện nhưng bị khóa vì lý do này. */
    private record ReviewFlags(boolean allowed, String blockedReason) {
    }
}