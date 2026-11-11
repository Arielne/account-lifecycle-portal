package io.github.arielne.alp.ticket;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu kèm hồ sơ khách đầy đủ cho màn sửa và gửi lại (TicketMapper.findEditData, CN-12, D2). Đọc chéo account,
 * customer. CCCD, điện thoại, email là bản đầy đủ: chỉ trả về khi người tạo sửa phiếu Mở tài khoản cho khách mới.
 */
@Getter
@Setter
public class TicketEditRow {

    private Long id;
    private String code;
    private String type;
    private String status;
    private String fromStatus;
    private String toStatus;
    private boolean newCustomer;
    private String reason;
    private String rejectReason;
    private int submitCount;
    private String accountNumber;
    private Long accountTypeId;
    private Long customerId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String cccd;
    private LocalDate idIssueDate;
    private String idIssuePlace;
    private String phone;
    private String email;
    private String address;
}