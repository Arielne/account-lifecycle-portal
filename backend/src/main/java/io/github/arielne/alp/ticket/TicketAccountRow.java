package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Tài khoản theo số tài khoản, đọc chéo bảng account, customer, account_type, branch (TicketMapper.findAccount):
 * dùng để kiểm tài khoản có tạo được phiếu không (eligibility) và lúc tạo phiếu.
 * customerCccd là số đầy đủ: chỉ dùng nội bộ, che trước khi trả về (QT-06).
 * openTicketCode, openTicketStatus: phiếu đang Chờ duyệt hoặc Bị từ chối của tài khoản (QT-03), rỗng nếu không có.
 */
@Getter
@Setter
public class TicketAccountRow {

    private Long accountId;
    private String accountNumber;
    private String status;
    private Long customerId;
    private String customerName;
    private String customerCccd;
    private String accountTypeName;
    private String branchName;
    private String openTicketCode;
    private String openTicketStatus;
}