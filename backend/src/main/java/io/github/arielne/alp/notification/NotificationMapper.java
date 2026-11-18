package io.github.arielne.alp.notification;

import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis của notification (CN-05, CN-24): chỉ đọc chéo bảng của module khác (ticket, account, customer,
 * employee) để có dữ liệu gửi mail. Câu SQL nằm trong NotificationMapper.xml. notification không ghi bảng nào: phiếu
 * sang Hoàn tất thì gọi TicketService của ticket.
 */
@Mapper
public interface NotificationMapper {

    /** Dữ liệu để gửi mail kết quả của một phiếu; không có phiếu thì null. */
    TicketMailData findTicketMail(Long ticketId);

    /** Họ tên, email của nhân viên; không có thì null. */
    EmployeeMailData findEmployeeMail(Long employeeId);
}