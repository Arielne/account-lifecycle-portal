package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng của màn Chờ duyệt (CN-14, spec phiếu mục 10.3), dùng làm cả kết quả MyBatis lẫn JSON trả về vì không có
 * dữ liệu phải che: không có CCCD, điện thoại, email (QT-06). waitingMinutes, overdue do database tính (V13).
 * fromStatus để React gọi đúng tên "Kích hoạt lại" cho phiếu Gỡ phong tỏa tài khoản Ngủ đông.
 * accountNumber, customerName rỗng với phiếu lô (không gắn tài khoản).
 */
@Getter
@Setter
public class PendingTicketItem {

    private Long id;
    private String code;
    private String type;
    private String fromStatus;
    private int submitCount;
    private boolean newCustomer;
    private OffsetDateTime lastSubmittedAt;
    private int waitingMinutes;
    private boolean overdue;
    private String accountNumber;
    private String customerName;
}