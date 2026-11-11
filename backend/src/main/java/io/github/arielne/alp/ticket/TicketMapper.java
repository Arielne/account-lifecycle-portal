package io.github.arielne.alp.ticket;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper MyBatis của module phiếu (CN-11, CN-15): ghi bảng ticket, attachment, ticket_history; đọc chéo bảng
 * account, customer, account_type, employee, branch (chỉ đọc). Câu SQL nằm trong TicketMapper.xml.
 */
@Mapper
public interface TicketMapper {

    /** Phần số của mã phiếu, lấy từ app.ticket_code_seq. */
    long nextCodeNumber();

    /** Thêm phiếu; id do database sinh được gán lại vào record. */
    int insertTicket(TicketRecord record);

    int insertAttachment(@Param("ticketId") Long ticketId, @Param("originalName") String originalName,
                         @Param("storageKey") String storageKey, @Param("contentType") String contentType,
                         @Param("sizeBytes") long sizeBytes, @Param("uploadedBy") Long uploadedBy);

    int insertHistory(@Param("ticketId") Long ticketId, @Param("action") String action,
                      @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                      @Param("actorId") Long actorId, @Param("note") String note);

    /** Tài khoản theo số, kèm phiếu đang mở của nó; không có thì null. */
    TicketAccountRow findAccountByNumber(String accountNumber);

    /** Hồ sơ khách theo CCCD hoặc theo id (truyền một trong hai), kèm trạng thái D5, D6; không có thì null. */
    CustomerStateRow findCustomerState(@Param("cccd") String cccd, @Param("customerId") Long customerId);

    /** Một phiếu kèm thông tin để hiện ở chi tiết; không có thì null. */
    TicketDetailRow findDetail(Long id);

    /** Giấy tờ đang dùng của phiếu (chưa bị bỏ), theo thứ tự tải lên. */
    List<AttachmentRow> findActiveAttachments(Long ticketId);

    /** Một giấy tờ đang dùng, chỉ khi nó thuộc đúng phiếu trên URL (QT-10); không thì null. */
    AttachmentRow findActiveAttachment(@Param("ticketId") Long ticketId, @Param("attachmentId") Long attachmentId);

    /** Lịch sử xử lý, mới nhất ở trên. */
    List<TicketHistoryRow> findHistory(Long ticketId);

    /**
     * Khóa dòng phiếu tới hết transaction (SELECT ... FOR UPDATE) cho bước duyệt, từ chối, sửa, hủy; không có thì
     * null.
     */
    TicketReviewRow lockForReview(Long id);

    /** Như lockForReview nhưng không khóa, chỉ để đọc và kiểm quyền; không có thì null. */
    TicketReviewRow findForRevision(Long id);

    /** Phiếu theo mã, cho service task của Camunda (biến quy trình chỉ có mã phiếu); không có thì null. */
    TicketReviewRow findForWorkflow(String code);

    /** Ghi kết quả duyệt hoặc từ chối: trạng thái mới, người và lúc xử lý, lý do từ chối (rỗng khi duyệt). */
    int updateReview(@Param("id") Long id, @Param("status") String status, @Param("reviewedBy") Long reviewedBy,
                     @Param("rejectReason") String rejectReason);

    /** Gửi lại phiếu Bị từ chối với lý do mới (mục 7.3). */
    int resubmitTicket(@Param("id") Long id, @Param("reason") String reason);

    /** Phiếu sang Đã hủy (mục 7.4). */
    int cancelTicket(Long id);

    /** Bỏ mềm các giấy tờ đang dùng của phiếu (D3); trả số dòng đã bỏ. */
    int removeAttachments(@Param("ticketId") Long ticketId, @Param("attachmentIds") Long[] attachmentIds,
                          @Param("removedBy") Long removedBy);

    /** Phiếu kèm hồ sơ khách đầy đủ cho màn sửa phiếu (D2); không có thì null. */
    TicketEditRow findEditData(Long id);

    /** Một trang của Tất cả phiếu khớp điều kiện lọc, mới cập nhật ở trên. */
    List<TicketListItem> findPage(TicketListFilter filter);

    /** Tổng số phiếu khớp điều kiện lọc (không tính phân trang). */
    long count(TicketListFilter filter);

    /** Số phiếu theo từng trạng thái, theo mọi bộ lọc trừ bộ lọc trạng thái (D8); trạng thái không có phiếu thì không có dòng. */
    List<TicketStatusCount> countByStatus(TicketListFilter filter);

    /** Phiếu Chờ duyệt của một chi nhánh, trừ phiếu do chính người xem tạo (QT-01, QT-04); gửi sớm nhất ở trên. */
    List<PendingTicketItem> findPendingReview(PendingReviewFilter filter);

    /** Số phiếu Chờ duyệt và số phiếu quá 24 giờ, cùng điều kiện với findPendingReview nhưng không lọc mã, loại. */
    PendingReviewSummary countPendingReview(@Param("branchId") Long branchId, @Param("viewerId") Long viewerId);
}