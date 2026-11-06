package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON của GET /api/tickets/pending-review/summary: số phiếu đang chờ người xem duyệt, cho badge của mục menu
 * "Chờ duyệt" (D9), và số phiếu quá 24 giờ trong đó.
 */
@Getter
@Setter
public class PendingReviewSummary {

    private long pendingCount;
    private long overdueCount;
}