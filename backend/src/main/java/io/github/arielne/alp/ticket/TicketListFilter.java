package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Điều kiện của TicketMapper.findPage, count, countByStatus, đổi từ TicketListQuery. Khoảng ngày đã đổi thành 2 mốc
 * thời gian theo giờ Việt Nam: createdFrom là 0 giờ ngày đầu, createdBefore là 0 giờ ngày sau ngày cuối.
 */
@Getter
@Setter
public class TicketListFilter {

    private String[] statuses;
    private String codePrefix;
    private String type;
    private Long branchId;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdBefore;
    private Long accountId;
    private Long customerId;
    private long offset;
    private int size;
}