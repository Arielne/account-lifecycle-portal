package io.github.arielne.alp.ticket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Thân của POST /api/tickets/customer-check (CN-11). Gửi bằng POST để số CCCD không nằm trên URL (QT-05). */
public record CustomerCheckRequest(
        @NotNull(message = "Vui lòng nhập số CCCD")
        @Pattern(regexp = "\\d{12}", message = "Số CCCD phải đủ 12 chữ số")
        String cccd) {
}