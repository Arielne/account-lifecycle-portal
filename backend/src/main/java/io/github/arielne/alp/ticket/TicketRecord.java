package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng app.ticket để INSERT (TicketMapper.insertTicket). Class có setter để MyBatis gán lại id database sinh.
 * created_at, updated_at, last_submitted_at để database đặt mặc định now(): cùng một giá trị trong câu INSERT.
 */
@Getter
@Setter
public class TicketRecord {

    private Long id;
    private String code;
    private String type;
    private String status;
    private Long accountId;
    private Long branchId;
    private Long createdBy;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private int submitCount;
    private String processInstanceId;
    private boolean newCustomer;
}