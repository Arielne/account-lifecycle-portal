package io.github.arielne.alp.ticket;

/** Kết quả POST /api/tickets (CN-11): id để mở trang chi tiết, mã phiếu để hiện "Đã gửi phiếu PYC-...". */
public record TicketCreated(Long id, String code) {
}