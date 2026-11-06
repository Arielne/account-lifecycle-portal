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
}