package io.github.arielne.alp.ticket;

import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final int PAGE_SIZE = 10;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TicketMapper ticketMapper;
    private final TicketAttachmentService attachmentService;
    private final CurrentEmployeeService currentEmployeeService;

    /**
     * Tất cả phiếu (CN-14), Lịch sử phiếu của tài khoản, tab Phiếu yêu cầu của khách hàng: một trang 10 phiếu của
     * mọi chi nhánh (QT-04), tổng số, và số phiếu theo từng trạng thái theo các bộ lọc khác (D8).
     */
    public TicketListPage findPage(TicketListQuery query) {
        TicketListFilter filter = toFilter(query);
        return new TicketListPage(ticketMapper.findPage(filter), ticketMapper.count(filter), statusCounts(filter));
    }

    /** Đủ 5 trạng thái theo thứ tự của enum; trạng thái không có phiếu nào thì 0. */
    private Map<String, Long> statusCounts(TicketListFilter filter) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (TicketStatusCount row : ticketMapper.countByStatus(filter)) {
            counts.put(row.getStatus(), row.getCount());
        }
        return counts;
    }

    /**
     * Khoảng ngày theo giờ Việt Nam (D8): từ 0 giờ ngày đầu tới trước 0 giờ ngày sau ngày cuối. So thẳng ngày với
     * created_at (giờ UTC) thì phiếu tạo từ 0 giờ tới 7 giờ sáng bị tính sang ngày trước. Trang nhỏ hơn 1 là trang 1.
     */
    private TicketListFilter toFilter(TicketListQuery query) {
        TicketListFilter filter = new TicketListFilter();
        filter.setStatuses(toStatusCodes(query.statuses()));
        if (query.code() != null && !query.code().isBlank()) {
            filter.setCodePrefix(query.code().trim().toUpperCase(Locale.ROOT));
        }
        if (query.type() != null) {
            filter.setType(query.type().name());
        }
        filter.setBranchId(query.branchId());
        if (query.createdFrom() != null) {
            filter.setCreatedFrom(query.createdFrom().atStartOfDay(VIETNAM).toOffsetDateTime());
        }
        if (query.createdTo() != null) {
            filter.setCreatedBefore(query.createdTo().plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
        }
        filter.setAccountId(query.accountId());
        filter.setCustomerId(query.customerId());
        int page = 1;
        if (query.page() != null && query.page() > 1) {
            page = query.page();
        }
        // Tính bằng long: trang gần Integer.MAX_VALUE nhân 10 sẽ tràn int thành số âm.
        filter.setOffset((page - 1L) * PAGE_SIZE);
        filter.setSize(PAGE_SIZE);
        return filter;
    }

    /** Danh sách enum thành mảng mã cho câu SQL; không chọn trạng thái nào thì null, tức không lọc. */
    private String[] toStatusCodes(List<TicketStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        String[] codes = new String[statuses.size()];
        for (int index = 0; index < statuses.size(); index++) {
            codes[index] = statuses.get(index).name();
        }
        return codes;
    }

    /** Chi tiết một phiếu kèm giấy tờ đang dùng, lịch sử xử lý và cờ nút cho người đang xem; không có thì 404. */
    public TicketDetail findDetail(Long id) {
        TicketDetailRow row = ticketMapper.findDetail(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
        }
        ReviewFlags review = reviewFlagsFor(row);
        RevisionFlags revision = revisionFlagsFor(row);
        return new TicketDetail(row.getId(), row.getCode(), row.getType(), row.getStatus(), row.getAccountId(),
                row.getAccountNumber(), row.getCustomerId(), row.getCustomerName(),
                PersonalDataMasker.maskCccd(row.getCustomerCccd()), row.getAccountTypeName(), row.getFromStatus(),
                row.getToStatus(), row.isNewCustomer(), row.getCreatedByName(), row.getBranchName(),
                row.getReviewedByName(), row.getReason(), row.getRejectReason(), row.getSubmitCount(),
                row.getCreatedAt(), row.getLastSubmittedAt(), attachmentsOf(id), historyOf(id),
                review.allowed(), review.allowed(), review.blockedReason(),
                revision.canEdit(), revision.canCancel(), revision.onBehalf());
    }

    /**
     * Cờ nút Sửa và gửi lại, Hủy phiếu (spec phiếu mục 10.5), cùng luật với TicketRevisionService (TicketPermissions):
     * chỉ Giao dịch viên. Hủy thay (onBehalf) là khi người xem không phải người tạo nhưng được hủy (D12).
     */
    private RevisionFlags revisionFlagsFor(TicketDetailRow row) {
        if (!currentEmployeeService.currentUserHasRole("MAKER")) {
            return new RevisionFlags(false, false, false);
        }
        CurrentEmployee viewer = currentEmployeeService.get();
        boolean canEdit = TicketPermissions.canEdit(row.getStatus(), row.getCreatorKeycloakId(), viewer);
        boolean canCancel = TicketPermissions.canCancel(row.getStatus(), row.getCreatorKeycloakId(),
                row.getCreatorStatus(), row.getBranchId(), viewer);
        boolean onBehalf = canCancel && !TicketPermissions.isCreator(row.getCreatorKeycloakId(), viewer);
        return new RevisionFlags(canEdit, canCancel, onBehalf);
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

    /** Được sửa, được hủy, và hủy đó có phải hủy thay người tạo đã bị khóa không. */
    private record RevisionFlags(boolean canEdit, boolean canCancel, boolean onBehalf) {
    }
}