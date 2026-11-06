package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Điều kiện của TicketMapper.findPendingReview. branchId, viewerId lấy từ người đang đăng nhập (QT-04), không nhận từ
 * request; codePrefix, type là ô lọc của màn Chờ duyệt, rỗng là không lọc.
 */
@Getter
@Setter
public class PendingReviewFilter {

    private Long branchId;
    private Long viewerId;
    private String codePrefix;
    private String type;
}