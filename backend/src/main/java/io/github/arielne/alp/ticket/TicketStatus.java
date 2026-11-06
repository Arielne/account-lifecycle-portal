package io.github.arielne.alp.ticket;

/** 5 trạng thái của phiếu (docs/01-chuc-nang.md mục 2.3); database chặn mã khác bằng CHECK (V5). */
public enum TicketStatus {
    PENDING_APPROVAL,
    REJECTED,
    APPROVED,
    COMPLETED,
    CANCELLED
}