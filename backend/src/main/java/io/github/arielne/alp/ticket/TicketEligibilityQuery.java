package io.github.arielne.alp.ticket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Tham số của GET /api/tickets/eligibility?type=&accountNumber= (CN-11). Số tài khoản không phải dữ liệu cá nhân. */
public record TicketEligibilityQuery(
        @NotNull(message = "Chọn loại phiếu")
        TicketType type,

        @NotNull(message = "Nhập số tài khoản")
        @Pattern(regexp = "\\d{10}", message = "Số tài khoản gồm 10 chữ số")
        String accountNumber) {
}