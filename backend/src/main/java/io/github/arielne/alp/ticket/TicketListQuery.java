package io.github.arielne.alp.ticket;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Tham số lọc của GET /api/tickets (CN-14, spec phiếu mục 10.4, 10.6), ví dụ
 * ?statuses=PENDING_APPROVAL,REJECTED&code=PYC-2026&createdFrom=2026-11-01&createdTo=2026-11-07&page=2.
 * Tham số nào không gửi thì là null, tức không lọc theo điều kiện đó. Ngày dạng 2026-11-05, tính theo giờ Việt Nam.
 * accountId, customerId dùng cho Lịch sử phiếu của tài khoản và tab Phiếu yêu cầu của khách hàng.
 */
public record TicketListQuery(
        List<TicketStatus> statuses,

        @Pattern(regexp = "[A-Za-z0-9-]{0,14}", message = "Mã phiếu chỉ gồm chữ, số và dấu -")
        String code,

        TicketType type,
        Long branchId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,

        Long accountId,
        Long customerId,
        Integer page) {
}