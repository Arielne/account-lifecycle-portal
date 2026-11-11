package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/** Số phiếu của một trạng thái (TicketMapper.countByStatus), cho số đếm cạnh Segmented ở Tất cả phiếu (D8). */
@Getter
@Setter
public class TicketStatusCount {

    private String status;
    private long count;
}