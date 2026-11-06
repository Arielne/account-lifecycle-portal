package io.github.arielne.alp.ticket;

import io.github.arielne.alp.account.AccountService;
import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.account.PendingAccount;
import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import io.github.arielne.alp.ticket.workflow.TicketWorkflow;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ tạo phiếu (CN-11, spec phiếu mục 7.1) và kiểm trước khi tạo (eligibility, customer-check).
 * Người tạo và chi nhánh của phiếu lấy từ hồ sơ người đang đăng nhập, không nhận từ request (QT-04).
 * Ghi bảng account, customer qua AccountService (ticket không tự ghi bảng của module khác).
 * Xem chi tiết phiếu và tải giấy tờ ở TicketQueryService.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TicketMapper ticketMapper;
    private final TicketAttachmentService attachmentService;
    private final TicketWorkflow ticketWorkflow;
    private final AccountService accountService;
    private final CurrentEmployeeService currentEmployeeService;
    private final AuditNote auditNote;

    /**
     * Tạo phiếu, mọi bước trong 1 transaction: kiểm khách hoặc tài khoản, lưu giấy tờ, mở tài khoản Chờ mở (phiếu
     * Mở tài khoản), lấy mã phiếu, bắt đầu quy trình Camunda, rồi INSERT phiếu, giấy tờ, lịch sử. Bước nào lỗi thì
     * mọi thứ cùng bị hủy, kể cả quy trình Camunda (chung transaction) và file vừa lưu (TicketAttachmentService).
     */
    @Transactional
    @Audited(action = "CREATE", objectType = "TICKET")
    public TicketCreated create(TicketCreateRequest request, List<MultipartFile> files) {
        CurrentEmployee creator = currentEmployeeWithBranch();
        TicketSubject subject;
        if (request.type() == TicketType.MO_TAI_KHOAN) {
            subject = prepareAccountOpening(request, files, creator);
        } else {
            subject = prepareExistingAccount(request, files);
        }
        String code = nextCode();
        // Cột process_instance_id bắt buộc nên bắt đầu quy trình trước câu INSERT; quy trình dừng ngay ở bước duyệt.
        String processInstanceId = ticketWorkflow.start(code, request.type().name());
        Long ticketId = insertTicket(request, subject, code, processInstanceId, creator);
        recordAudit(code, request.type(), subject);
        return new TicketCreated(ticketId, code);
    }

    /** Bước 2 của màn Tạo phiếu: tài khoản có tạo được phiếu loại này không, kèm lý do nếu không (CN-11). */
    public TicketEligibility checkEligibility(TicketType type, String accountNumber) {
        if (!type.needsExistingAccount()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Loại phiếu này không gắn với tài khoản có sẵn");
        }
        TicketAccountRow account = findAccount(accountNumber);
        String problem = eligibilityProblem(type, account);
        String maskedCccd = PersonalDataMasker.maskCccd(account.getCustomerCccd());
        return new TicketEligibility(account.getAccountId(), account.getAccountNumber(), account.getCustomerId(),
                account.getCustomerName(), maskedCccd, account.getAccountTypeName(), account.getBranchName(),
                account.getStatus(), type.getTargetStatus().name(), problem == null, problem);
    }

    /** Trạng thái của số CCCD cho phiếu Mở tài khoản (NEW, EXISTS, REUSABLE, PENDING; spec phiếu mục 7.1). */
    public CustomerCheckResult checkCustomer(String cccd) {
        CustomerStateRow row = ticketMapper.findCustomerState(cccd, null);
        if (row == null) {
            return new CustomerCheckResult(CustomerCccdState.NEW, null, null);
        }
        return new CustomerCheckResult(stateOf(row), row.getCustomerId(), row.getPendingTicketCode());
    }

    /** Phiếu Mở tài khoản: kiểm hồ sơ khách (D5, D6), lưu giấy tờ, rồi tạo hồ sơ nếu cần và tài khoản Chờ mở. */
    private TicketSubject prepareAccountOpening(TicketCreateRequest request, List<MultipartFile> files,
                                                CurrentEmployee creator) {
        if (request.customerId() != null) {
            requireCustomerNotPending(request.customerId());
            List<StoredAttachment> attachments = attachmentService.storeAll(files);
            PendingAccount account = accountService.openPendingForCustomer(request.customerId(),
                    request.accountTypeId(), creator.branchId());
            return new TicketSubject(account.accountId(), account.accountNumber(), AccountStatus.PENDING_OPEN,
                    false, false, attachments);
        }
        Long reusableCustomerId = requireCccdUsableForNewCustomer(request.newCustomer().cccd());
        List<StoredAttachment> attachments = attachmentService.storeAll(files);
        PendingAccount account = accountService.openPendingForNewCustomer(request.newCustomer().toProfile(),
                reusableCustomerId, request.accountTypeId(), creator.branchId());
        return new TicketSubject(account.accountId(), account.accountNumber(), AccountStatus.PENDING_OPEN,
                true, reusableCustomerId != null, attachments);
    }

    /** Phong tỏa, Gỡ phong tỏa, Đóng tài khoản: tài khoản đúng trạng thái, không có phiếu mở (QT-03), rồi lưu giấy tờ. */
    private TicketSubject prepareExistingAccount(TicketCreateRequest request, List<MultipartFile> files) {
        TicketAccountRow account = findAccount(request.accountNumber());
        String problem = eligibilityProblem(request.type(), account);
        if (problem != null) {
            throw new BusinessException(HttpStatus.CONFLICT, problem);
        }
        List<StoredAttachment> attachments = attachmentService.storeAll(files);
        return new TicketSubject(account.getAccountId(), account.getAccountNumber(),
                AccountStatus.valueOf(account.getStatus()), false, false, attachments);
    }

    private Long insertTicket(TicketCreateRequest request, TicketSubject subject, String code,
                              String processInstanceId, CurrentEmployee creator) {
        TicketRecord record = new TicketRecord();
        record.setCode(code);
        record.setType(request.type().name());
        record.setStatus(TicketStatus.PENDING_APPROVAL.name());
        record.setAccountId(subject.accountId());
        record.setBranchId(creator.branchId());
        record.setCreatedBy(creator.id());
        record.setFromStatus(subject.fromStatus().name());
        record.setToStatus(request.type().getTargetStatus().name());
        record.setReason(request.reason().trim());
        record.setSubmitCount(1);
        record.setProcessInstanceId(processInstanceId);
        record.setNewCustomer(subject.newCustomer());
        ticketMapper.insertTicket(record);
        for (StoredAttachment attachment : subject.attachments()) {
            ticketMapper.insertAttachment(record.getId(), attachment.originalName(), attachment.storageKey(),
                    attachment.contentType(), attachment.sizeBytes(), creator.id());
        }
        ticketMapper.insertHistory(record.getId(), TicketAction.SUBMITTED.name(), null,
                TicketStatus.PENDING_APPROVAL.name(), creator.id(), null);
        return record.getId();
    }

    /** Lý do tài khoản không tạo được phiếu loại này; null là tạo được. */
    private String eligibilityProblem(TicketType type, TicketAccountRow account) {
        if (account.getOpenTicketCode() != null) {
            String openState = "chờ duyệt";
            if (TicketStatus.REJECTED.name().equals(account.getOpenTicketStatus())) {
                openState = "bị từ chối";
            }
            return "Tài khoản đang có phiếu " + account.getOpenTicketCode() + " " + openState;
        }
        AccountStatus status = AccountStatus.valueOf(account.getStatus());
        if (!type.allowsFrom(status)) {
            return "Tài khoản đang " + status.getLabel() + ", không tạo được phiếu " + type.labelFor(status);
        }
        return null;
    }

    private TicketAccountRow findAccount(String accountNumber) {
        TicketAccountRow account = ticketMapper.findAccountByNumber(accountNumber);
        if (account == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản " + accountNumber);
        }
        return account;
    }

    /** Khách đã có: hồ sơ phải tồn tại và không đang chờ duyệt ở phiếu Mở tài khoản khác (D5). */
    private void requireCustomerNotPending(Long customerId) {
        CustomerStateRow row = ticketMapper.findCustomerState(null, customerId);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng");
        }
        if (row.getPendingTicketCode() != null) {
            throw pendingProfile(row.getPendingTicketCode());
        }
    }

    /**
     * Khách mới: số CCCD chưa có hồ sơ (trả null), hoặc hồ sơ có số đó chưa từng mở được tài khoản (trả id hồ sơ
     * để ghi đè, D6). Đã có hồ sơ thật hoặc hồ sơ đang chờ duyệt thì 409.
     */
    private Long requireCccdUsableForNewCustomer(String cccd) {
        CustomerStateRow row = ticketMapper.findCustomerState(cccd, null);
        if (row == null) {
            return null;
        }
        CustomerCccdState state = stateOf(row);
        if (state == CustomerCccdState.PENDING) {
            throw pendingProfile(row.getPendingTicketCode());
        }
        if (state == CustomerCccdState.EXISTS) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Số CCCD đã có hồ sơ khách hàng. Hãy chọn Khách hàng đã có");
        }
        return row.getCustomerId();
    }

    private CustomerCccdState stateOf(CustomerStateRow row) {
        if (row.getPendingTicketCode() != null) {
            return CustomerCccdState.PENDING;
        }
        if (row.isReusable()) {
            return CustomerCccdState.REUSABLE;
        }
        return CustomerCccdState.EXISTS;
    }

    private BusinessException pendingProfile(String ticketCode) {
        return new BusinessException(HttpStatus.CONFLICT, "Hồ sơ khách đang chờ duyệt ở phiếu " + ticketCode);
    }

    /** Người tạo chưa có chi nhánh thì 409 (bình thường không xảy ra: Admin chỉ gán MAKER khi đã có chi nhánh). */
    private CurrentEmployee currentEmployeeWithBranch() {
        CurrentEmployee employee = currentEmployeeService.get();
        if (employee.branchId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "Bạn chưa được gán chi nhánh nên chưa tạo được phiếu");
        }
        return employee;
    }

    /** Mã phiếu dạng PYC-2026-0001: năm theo giờ Việt Nam, số từ sequence (không đếm lại theo năm nên không trùng). */
    private String nextCode() {
        int year = LocalDate.now(VIETNAM).getYear();
        return "PYC-" + year + "-" + String.format("%04d", ticketMapper.nextCodeNumber());
    }

    /** Mô tả audit theo lời nghiệp vụ, ví dụ "Phong tỏa · TK 1024118603 · Hoạt động → Phong tỏa" (spec mục 12). */
    private void recordAudit(String code, TicketType type, TicketSubject subject) {
        String description = type.labelFor(subject.fromStatus()) + " · TK " + subject.accountNumber() + " · "
                + subject.fromStatus().getLabel() + " → " + type.getTargetStatus().getLabel();
        if (subject.reusedProfile()) {
            description = description + " · cập nhật hồ sơ chưa từng mở tài khoản";
        }
        auditNote.setObjectId(code);
        auditNote.setDescription(description);
    }

    /**
     * Tài khoản mà phiếu gắn vào, trạng thái lúc tạo phiếu, phiếu có tạo hồ sơ khách mới không (reusedProfile: ghi
     * đè hồ sơ cũ, D6), và giấy tờ đã lưu.
     */
    private record TicketSubject(Long accountId, String accountNumber, AccountStatus fromStatus,
                                 boolean newCustomer, boolean reusedProfile, List<StoredAttachment> attachments) {
    }
}