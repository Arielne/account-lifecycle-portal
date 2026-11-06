package io.github.arielne.alp.ticket;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Một dòng lịch sử xử lý phiếu (app.ticket_history) kèm họ tên người làm; actorName rỗng là Hệ thống. */
@Getter
@Setter
public class TicketHistoryRow {

    private String action;
    private String fromStatus;
    private String toStatus;
    private String actorName;
    private String note;
    private OffsetDateTime createdAt;
}