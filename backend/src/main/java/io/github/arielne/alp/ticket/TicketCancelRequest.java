package io.github.arielne.alp.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Thân request của POST /api/tickets/{id}/cancel (CN-13): lý do hủy bắt buộc, tối đa 500 ký tự (D4). */
public record TicketCancelRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy")
        @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
        String reason) {
}