package io.github.arielne.alp.ticket;

import java.util.List;
import java.util.Map;

/**
 * JSON của GET /api/tickets: một trang 10 phiếu, tổng số phiếu khớp mọi bộ lọc, và số phiếu theo từng trạng thái
 * đếm theo các bộ lọc khác (không tính bộ lọc trạng thái, D8). statusCounts có đủ 5 trạng thái, không có phiếu thì 0.
 */
public record TicketListPage(List<TicketListItem> items, long total, Map<String, Long> statusCounts) {
}