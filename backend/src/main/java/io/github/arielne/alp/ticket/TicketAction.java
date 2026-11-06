package io.github.arielne.alp.ticket;

/**
 * Mã hành động trong lịch sử xử lý phiếu (bảng ticket_history, spec phiếu mục 3.1); database chặn mã khác (V12).
 * Màn chi tiết phiếu đổi mã sang chữ tiếng Việt.
 */
public enum TicketAction {
    SUBMITTED,
    RESUBMITTED,
    REJECTED,
    APPROVED,
    CANCELLED,
    COMPLETED
}