package io.github.arielne.alp.ticket;

import io.github.arielne.alp.account.AccountService;
import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.customer.CustomerProfile;
import io.github.arielne.alp.ticket.workflow.TicketWorkflow;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sửa và gửi lại phiếu Bị từ chối (CN-12, spec phiếu mục 7.3) và hủy phiếu (CN-13, mục 7.4).
 * Người làm lấy từ hồ sơ người đang đăng nhập (QT-04). Ghi bảng account, customer qua AccountService.
 */
@Service
@RequiredArgsConstructor
public class TicketRevisionService {

    static final String ONLY_CREATOR_EDITS = "Chỉ người tạo phiếu được sửa";
    static final String ONLY_CREATOR_CANCELS = "Chỉ người tạo phiếu được hủy";
    static final String ONLY_REJECTED_EDITABLE = "Chỉ sửa được phiếu đang Bị từ chối";

    private final TicketMapper ticketMapper;
    private final TicketAttachmentService attachmentService;
    private final TicketWorkflow ticketWorkflow;
    private final AccountService accountService;
    private final CurrentEmployeeService currentEmployeeService;
    private final AuditNote auditNote;

    /** Dữ liệu điền sẵn cho màn sửa phiếu: chỉ người tạo (403), chỉ phiếu Bị từ chối (409). */
    public TicketEditData findEditData(Long ticketId) {
        TicketReviewRow ticket = requireTicket(ticketMapper.findForRevision(ticketId));
        requireEditableBy(ticket, currentEmployeeService.get());
        TicketEditRow row = ticketMapper.findEditData(ticketId);
        TicketEditData.CustomerData customer = null;
        if (row.isNewCustomer()) {
            customer = new TicketEditData.CustomerData(row.getFullName(), row.getDateOfBirth(), row.getGender(),
                    row.getCccd(), row.getIdIssueDate(), row.getIdIssuePlace(), row.getPhone(), row.getEmail(),
                    row.getAddress());
        }
        return new TicketEditData(row.getId(), row.getCode(), row.getType(), row.getFromStatus(), row.getToStatus(),
                row.getAccountNumber(), row.getAccountTypeId(), row.getCustomerId(), customer, row.getReason(),
                row.getRejectReason(), row.getSubmitCount(), activeAttachments(ticketId));
    }

    /**
     * Gửi lại phiếu, mọi bước trong 1 transaction: khóa phiếu, kiểm người tạo và trạng thái, kiểm tài khoản vẫn đúng
     * trạng thái lúc tạo phiếu, bỏ và thêm giấy tờ, sửa tài khoản Chờ mở (phiếu Mở tài khoản), ghi phiếu và lịch sử,
     * rồi hoàn thành bước sửa của Camunda để quy trình về bước duyệt.
     */
    @Transactional
    @Audited(action = "RESUBMIT", objectType = "TICKET")
    public void resubmit(Long ticketId, TicketResubmitRequest request, List<MultipartFile> files) {
        CurrentEmployee editor = currentEmployeeService.get();
        TicketReviewRow ticket = requireTicket(ticketMapper.lockForReview(ticketId));
        requireEditableBy(ticket, editor);
        requireAccountUnchanged(ticket);
        String taskId = ticketWorkflow.findReviseTaskId(ticket.getProcessInstanceId());
        if (taskId == null) {
            throw new BusinessException(HttpStatus.CONFLICT, ONLY_REJECTED_EDITABLE);
        }
        List<String> removedNames = removeAttachments(ticket, request.removedAttachmentIdsOrEmpty(), editor);
        int keptCount = ticketMapper.findActiveAttachments(ticketId).size();
        List<StoredAttachment> added = attachmentService.storeAdditional(files, keptCount);
        reviseAccount(ticket, request);
        ticketMapper.resubmitTicket(ticketId, request.reason().trim());
        for (StoredAttachment attachment : added) {
            ticketMapper.insertAttachment(ticketId, attachment.originalName(), attachment.storageKey(),
                    attachment.contentType(), attachment.sizeBytes(), editor.id());
        }
        ticketMapper.insertHistory(ticketId, TicketAction.RESUBMITTED.name(), TicketStatus.REJECTED.name(),
                TicketStatus.PENDING_APPROVAL.name(), editor.id(), attachmentChangeNote(removedNames, added));
        ticketWorkflow.completeRevise(taskId);
        auditNote.setObjectId(ticket.getCode());
        auditNote.setDescription(TicketDescriptions.accountChange(TicketType.valueOf(ticket.getType()),
                ticket.getAccountNumber(), AccountStatus.valueOf(ticket.getFromStatus()),
                AccountStatus.valueOf(ticket.getToStatus())));
    }

    /**
     * Hủy phiếu (mục 7.4): người tạo, hoặc Giao dịch viên cùng chi nhánh khi người tạo đã bị khóa (D12). Phiếu Mở tài
     * khoản thì tài khoản Chờ mở sang Đã đóng, hồ sơ khách giữ nguyên (D6); loại khác thì tài khoản giữ nguyên. Xóa
     * quy trình Camunda; không gửi mail.
     */
    @Transactional
    @Audited(action = "CANCEL", objectType = "TICKET")
    public void cancel(Long ticketId, String reason) {
        CurrentEmployee canceller = currentEmployeeService.get();
        String trimmedReason = reason.trim();
        TicketReviewRow ticket = requireTicket(ticketMapper.lockForReview(ticketId));
        boolean onBehalf = requireCancellableBy(ticket, canceller);
        boolean opensAccount = TicketType.MO_TAI_KHOAN.name().equals(ticket.getType());
        if (opensAccount) {
            accountService.changeStatus(ticket.getAccountId(), AccountStatus.PENDING_OPEN, AccountStatus.CLOSED,
                    ticketId, canceller.id());
        }
        ticketMapper.cancelTicket(ticketId);
        ticketMapper.insertHistory(ticketId, TicketAction.CANCELLED.name(), ticket.getStatus(),
                TicketStatus.CANCELLED.name(), canceller.id(), trimmedReason);
        ticketWorkflow.cancel(ticket.getProcessInstanceId());
        auditNote.setObjectId(ticket.getCode());
        auditNote.setDescription(describeCancel(ticket, opensAccount, trimmedReason, onBehalf));
    }

    private TicketReviewRow requireTicket(TicketReviewRow ticket) {
        if (ticket == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
        }
        return ticket;
    }

    /** Chỉ người tạo (403), chỉ phiếu Bị từ chối (409); người hủy thay (D12) không được sửa. */
    private void requireEditableBy(TicketReviewRow ticket, CurrentEmployee editor) {
        if (!TicketPermissions.isCreator(ticket.getCreatorKeycloakId(), editor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ONLY_CREATOR_EDITS);
        }
        if (!TicketStatus.REJECTED.name().equals(ticket.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, ONLY_REJECTED_EDITABLE);
        }
    }

    /**
     * Phiếu có thể nằm ở Bị từ chối nhiều ngày; trong lúc đó job Ngủ đông có thể đã đổi trạng thái tài khoản. Không
     * kiểm ở đây thì phiếu gửi lại được nhưng chắc chắn bị chặn ở bước duyệt (D7).
     */
    private void requireAccountUnchanged(TicketReviewRow ticket) {
        if (ticket.getFromStatus().equals(ticket.getAccountStatus())) {
            return;
        }
        String from = AccountStatus.valueOf(ticket.getFromStatus()).getLabel();
        String now = AccountStatus.valueOf(ticket.getAccountStatus()).getLabel();
        throw new BusinessException(HttpStatus.CONFLICT, "Trạng thái tài khoản đã đổi từ " + from + " sang " + now
                + ". Hãy hủy phiếu này và tạo phiếu mới.");
    }

    /**
     * Phiếu Chờ duyệt hoặc Bị từ chối (không thì 409, QT-11); người tạo hoặc người hủy thay hợp lệ (không thì 403).
     * Trả true khi là hủy thay người tạo đã bị khóa.
     */
    private boolean requireCancellableBy(TicketReviewRow ticket, CurrentEmployee canceller) {
        boolean open = TicketStatus.PENDING_APPROVAL.name().equals(ticket.getStatus())
                || TicketStatus.REJECTED.name().equals(ticket.getStatus());
        if (!open) {
            throw new BusinessException(HttpStatus.CONFLICT, TicketReviewService.HANDLED_BY_OTHER);
        }
        if (TicketPermissions.isCreator(ticket.getCreatorKeycloakId(), canceller)) {
            return false;
        }
        if (TicketPermissions.canCancelOnBehalf(ticket.getCreatorStatus(), ticket.getBranchId(), canceller)) {
            return true;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, ONLY_CREATOR_CANCELS);
    }

    /**
     * Bỏ mềm các giấy tờ được chọn (D3). Id phải là giấy tờ đang dùng của đúng phiếu này, không thì 400 và không
     * bỏ gì. Trả tên các giấy tờ đã bỏ để ghi vào lịch sử xử lý.
     */
    private List<String> removeAttachments(TicketReviewRow ticket, List<Long> attachmentIds, CurrentEmployee editor) {
        List<String> removedNames = new ArrayList<>();
        if (attachmentIds.isEmpty()) {
            return removedNames;
        }
        List<AttachmentRow> active = ticketMapper.findActiveAttachments(ticket.getId());
        for (Long attachmentId : attachmentIds) {
            AttachmentRow attachment = findById(active, attachmentId);
            if (attachment == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Giấy tờ cần bỏ không thuộc phiếu này");
            }
            removedNames.add(attachment.getOriginalName());
        }
        ticketMapper.removeAttachments(ticket.getId(), attachmentIds.toArray(new Long[0]), editor.id());
        return removedNames;
    }

    private AttachmentRow findById(List<AttachmentRow> attachments, Long attachmentId) {
        for (AttachmentRow attachment : attachments) {
            if (attachment.getId().equals(attachmentId)) {
                return attachment;
            }
        }
        return null;
    }

    /**
     * Phiếu Mở tài khoản: đổi loại tài khoản; phiếu tạo hồ sơ khách mới thì sửa cả hồ sơ (D2), đổi CCCD thì kiểm
     * trùng lại. 3 loại còn lại không có gì để sửa ngoài lý do và giấy tờ, gửi kèm dữ liệu này thì 400.
     */
    private void reviseAccount(TicketReviewRow ticket, TicketResubmitRequest request) {
        if (!TicketType.MO_TAI_KHOAN.name().equals(ticket.getType())) {
            if (request.accountTypeId() != null || request.newCustomer() != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Phiếu này chỉ sửa được lý do và giấy tờ");
            }
            return;
        }
        if (request.accountTypeId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chọn loại tài khoản");
        }
        CustomerProfile profile = null;
        if (ticket.isNewCustomer()) {
            if (request.newCustomer() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Thiếu hồ sơ khách hàng");
            }
            requireCccdFree(request.newCustomer().cccd(), ticket.getCustomerId());
            profile = request.newCustomer().toProfile();
        } else if (request.newCustomer() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phiếu cho khách đã có không sửa được hồ sơ khách");
        }
        accountService.revisePendingAccount(ticket.getAccountId(), request.accountTypeId(), profile,
                ticket.getBranchId());
    }

    /** Số CCCD mới không được thuộc hồ sơ khác (kể cả hồ sơ chưa từng mở tài khoản, vì CCCD là duy nhất). */
    private void requireCccdFree(String cccd, Long ownCustomerId) {
        CustomerStateRow row = ticketMapper.findCustomerState(cccd, null);
        if (row != null && !row.getCustomerId().equals(ownCustomerId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số CCCD đã có hồ sơ khách hàng");
        }
    }

    /** Ghi chú của dòng lịch sử gửi lại: tên giấy tờ đã bỏ và đã thêm (D3); không đổi giấy tờ thì null. */
    private String attachmentChangeNote(List<String> removedNames, List<StoredAttachment> added) {
        List<String> parts = new ArrayList<>();
        if (!removedNames.isEmpty()) {
            parts.add("Bỏ giấy tờ: " + String.join(", ", removedNames));
        }
        if (!added.isEmpty()) {
            List<String> addedNames = new ArrayList<>();
            for (StoredAttachment attachment : added) {
                addedNames.add(attachment.originalName());
            }
            parts.add("Thêm giấy tờ: " + String.join(", ", addedNames));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" · ", parts);
    }

    /** Giấy tờ đang dùng của phiếu, dạng hiện ở màn sửa. */
    private List<TicketDetail.Attachment> activeAttachments(Long ticketId) {
        List<TicketDetail.Attachment> attachments = new ArrayList<>();
        for (AttachmentRow row : ticketMapper.findActiveAttachments(ticketId)) {
            attachments.add(new TicketDetail.Attachment(row.getId(), row.getOriginalName(), row.getContentType(),
                    row.getSizeBytes()));
        }
        return attachments;
    }

    /** "Mở tài khoản · TK 1030000001 · Chờ mở → Đã đóng · Lý do: ... · hủy thay Nguyễn Thị Lan" (spec mục 12). */
    private String describeCancel(TicketReviewRow ticket, boolean opensAccount, String reason, boolean onBehalf) {
        AccountStatus from = AccountStatus.valueOf(ticket.getFromStatus());
        String description = TicketType.valueOf(ticket.getType()).labelFor(from) + " · TK " + ticket.getAccountNumber();
        if (opensAccount) {
            description = description + " · " + from.getLabel() + " → " + AccountStatus.CLOSED.getLabel();
        }
        description = TicketDescriptions.withReason(description, reason);
        if (onBehalf) {
            description = description + " · hủy thay " + ticket.getCreatorName();
        }
        return description;
    }
}