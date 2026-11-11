package io.github.arielne.alp.account;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng tài khoản MyBatis đọc ra, kèm họ tên, CCCD của chủ tài khoản và phiếu đang mở (nếu có).
 * customerCccd là số đầy đủ, chỉ dùng bên trong backend: AccountService che trước khi trả về (QT-06).
 */
@Getter
@Setter
public class AccountRow {
    private Long id;
    private String accountNumber;
    private Long customerId;
    private String customerName;
    private String customerCccd;
    private String accountTypeName;
    private String branchName;
    private String status;
    // Rỗng khi tài khoản còn Chờ mở.
    private LocalDate openedAt;
    // Rỗng khi tài khoản chưa đóng.
    private LocalDate closedAt;
    private LocalDate lastTransactionAt;
    // 3 cột của phiếu đang Chờ duyệt hoặc Bị từ chối; rỗng khi tài khoản không có phiếu đang mở.
    private String openTicketCode;
    private String openTicketType;
    private String openTicketStatus;
    // Chỉ findById đọc 2 cột này (spec phiếu mục 10.6): người tạo phiếu Mở tài khoản, người duyệt phiếu đó khi
    // phiếu Đã duyệt hoặc Hoàn tất. Tài khoản mẫu không mở qua phiếu thì rỗng.
    private String openedByName;
    private String openApprovedByName;
}