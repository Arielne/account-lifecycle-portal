package io.github.arielne.alp.ticket;

import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.ticket.workflow.TicketWorkflow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duyệt, từ chối phiếu (CN-16, CN-17, spec phiếu mục 8) và màn Chờ duyệt (CN-14).
 * Người duyệt và chi nhánh lấy từ hồ sơ người đang đăng nhập (QT-04). Backend tự kiểm người duyệt khác người tạo
 * (QT-01) và cùng chi nhánh với phiếu (QT-04) trước khi báo Camunda hoàn thành bước duyệt; khóa nút ở giao diện chỉ
 * là lớp chặn thứ hai.
 */
@Service
@RequiredArgsConstructor
public class TicketReviewService {

    static final String HANDLED_BY_OTHER = "Phiếu này vừa được người khác xử lý";
    static final String SELF_REVIEW = "Bạn không thể duyệt phiếu do chính mình tạo";

    private final TicketMapper ticketMapper;
    private final TicketWorkflow ticketWorkflow;
    private final CurrentEmployeeService currentEmployeeService;
    private final AuditNote auditNote;

    /**
     * Duyệt phiếu, mọi bước trong 1 transaction (mục 8.1): khóa phiếu, kiểm, ghi Đã duyệt và lịch sử, rồi hoàn thành
     * bước duyệt của Camunda. Camunda chạy tiếp ngay: đổi trạng thái tài khoản, ghi outbox, dừng ở bước chờ gửi mail.
     * Bước nào lỗi (kể cả trong service task) thì tất cả cùng bị hủy: phiếu vẫn Chờ duyệt, tài khoản giữ trạng thái
     * cũ. Ghi phiếu trước khi hoàn thành task để service task đọc được người duyệt.
     */
    @Transactional
    @Audited(action = "APPROVE", objectType = "TICKET")
    public void approve(Long ticketId) {
        CurrentEmployee reviewer = currentEmployeeService.get();
        TicketReviewRow ticket = lockReviewable(ticketId, reviewer);
        String taskId = requireReviewTask(ticket);
        ticketMapper.updateReview(ticketId, TicketStatus.APPROVED.name(), reviewer.id(), null);
        ticketMapper.insertHistory(ticketId, TicketAction.APPROVED.name(), TicketStatus.PENDING_APPROVAL.name(),
                TicketStatus.APPROVED.name(), reviewer.id(), null);
        ticketWorkflow.completeReview(taskId, TicketStatus.APPROVED.name());
        auditNote.setObjectId(ticket.getCode());
        auditNote.setDescription(describe(ticket));
    }

    /**
     * Từ chối phiếu (mục 8.2): cùng các bước kiểm như duyệt; ghi Bị từ chối kèm lý do, quy trình ghi outbox rồi dừng
     * ở bước chờ người tạo sửa. Trạng thái tài khoản không đổi. reason đã được kiểm 1–500 ký tự ở request.
     */
    @Transactional
    @Audited(action = "REJECT", objectType = "TICKET")
    public void reject(Long ticketId, String reason) {
        CurrentEmployee reviewer = currentEmployeeService.get();
        String trimmedReason = reason.trim();
        TicketReviewRow ticket = lockReviewable(ticketId, reviewer);
        String taskId = requireReviewTask(ticket);
        ticketMapper.updateReview(ticketId, TicketStatus.REJECTED.name(), reviewer.id(), trimmedReason);
        ticketMapper.insertHistory(ticketId, TicketAction.REJECTED.name(), TicketStatus.PENDING_APPROVAL.name(),
                TicketStatus.REJECTED.name(), reviewer.id(), trimmedReason);
        ticketWorkflow.completeReview(taskId, TicketStatus.REJECTED.name());
        auditNote.setObjectId(ticket.getCode());
        auditNote.setDescription(TicketDescriptions.withReason(describe(ticket), trimmedReason));
    }

    /**
     * Màn Chờ duyệt: phiếu Chờ duyệt của chi nhánh người xem, trừ phiếu người xem tự tạo, gửi sớm nhất ở trên, tối đa
     * 200 phiếu. code lọc theo phần đầu mã phiếu (không phân biệt hoa thường), type theo loại phiếu; rỗng là không lọc.
     */
    public List<PendingTicketItem> findPendingReview(String code, TicketType type) {
        CurrentEmployee viewer = currentEmployeeService.get();
        if (viewer.branchId() == null) {
            return List.of();
        }
        PendingReviewFilter filter = new PendingReviewFilter();
        filter.setBranchId(viewer.branchId());
        filter.setViewerId(viewer.id());
        filter.setCodePrefix(normalizeCode(code));
        if (type != null) {
            filter.setType(type.name());
        }
        return ticketMapper.findPendingReview(filter);
    }

    /** Số phiếu đang chờ người xem duyệt và số phiếu quá 24 giờ, cho badge của menu (D9). */
    public PendingReviewSummary summarizePendingReview() {
        CurrentEmployee viewer = currentEmployeeService.get();
        if (viewer.branchId() == null) {
            return new PendingReviewSummary();
        }
        return ticketMapper.countPendingReview(viewer.branchId(), viewer.id());
    }

    /**
     * Bước 1–3 của mục 8.1: khóa dòng phiếu (người bấm sau đứng chờ người bấm trước), phiếu không còn Chờ duyệt thì
     * 409 (QT-11), tự duyệt phiếu mình tạo thì 403 (QT-01), khác chi nhánh với phiếu thì 403 (QT-04).
     * So keycloak_id chứ không so role: người giữ cả MAKER và CHECKER vẫn không duyệt được phiếu mình tạo.
     */
    private TicketReviewRow lockReviewable(Long ticketId, CurrentEmployee reviewer) {
        TicketReviewRow ticket = ticketMapper.lockForReview(ticketId);
        if (ticket == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
        }
        if (!TicketStatus.PENDING_APPROVAL.name().equals(ticket.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, HANDLED_BY_OTHER);
        }
        if (ticket.getCreatorKeycloakId().equals(reviewer.keycloakId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, SELF_REVIEW);
        }
        if (!ticket.getBranchId().equals(reviewer.branchId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Chỉ kiểm soát viên " + ticket.getBranchName() + " được duyệt phiếu này");
        }
        return ticket;
    }

    /** Bước 4: quy trình phải đang dừng ở bước duyệt; không thì có người vừa xử lý xong (QT-11). */
    private String requireReviewTask(TicketReviewRow ticket) {
        String taskId = ticketWorkflow.findReviewTaskId(ticket.getProcessInstanceId());
        if (taskId == null) {
            throw new BusinessException(HttpStatus.CONFLICT, HANDLED_BY_OTHER);
        }
        return taskId;
    }

    private String describe(TicketReviewRow ticket) {
        return TicketDescriptions.accountChange(TicketType.valueOf(ticket.getType()), ticket.getAccountNumber(),
                AccountStatus.valueOf(ticket.getFromStatus()), AccountStatus.valueOf(ticket.getToStatus()));
    }

    /** Mã phiếu luôn viết hoa (PYC-...): đổi chữ đã gõ sang chữ hoa; chỉ có khoảng trắng thì coi như không lọc. */
    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
}