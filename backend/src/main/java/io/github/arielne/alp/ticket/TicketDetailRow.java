package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một phiếu kèm thông tin để hiện ở chi tiết phiếu (TicketMapper.findDetail), đọc chéo bảng account, customer,
 * account_type, employee, branch. customerCccd là số đầy đủ, che trước khi trả về (QT-06).
 */
@Getter
@Setter
public class TicketDetailRow {

    private Long id;
    private String code;
    private String type;
    private String status;
    private Long accountId;
    private String accountNumber;
    private Long customerId;
    private String customerName;
    private String customerCccd;
    private String accountTypeName;
    private String fromStatus;
    private String toStatus;
    private boolean newCustomer;
    private String createdByName;
    private String branchName;
    private String reviewedByName;
    private String reason;
    private String rejectReason;
    private int submitCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSubmittedAt;
}