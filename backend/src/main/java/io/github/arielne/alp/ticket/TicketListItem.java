package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng của Tất cả phiếu, Lịch sử phiếu (tài khoản) và tab Phiếu yêu cầu (khách hàng), dùng làm cả kết quả
 * MyBatis lẫn JSON vì không có dữ liệu phải che (không có CCCD, điện thoại, email; QT-06).
 * fromStatus để React gọi đúng tên "Kích hoạt lại"; accountNumber, customerName rỗng với phiếu lô.
 */
@Getter
@Setter
public class TicketListItem {

    private Long id;
    private String code;
    private String type;
    private String status;
    private String fromStatus;
    private String rejectReason;
    private boolean newCustomer;
    private String accountNumber;
    private String customerName;
    private String branchName;
    private String createdByName;
    private String reviewedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}