package io.github.arielne.alp.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân request của POST /api/tickets/{id}/reject (CN-17): lý do từ chối 1–500 ký tự, gửi trong mail cho người tạo
 * phiếu. Chỉ có khoảng trắng tính là chưa nhập; Service bỏ khoảng trắng hai đầu trước khi lưu.
 */
public record TicketRejectRequest(
        @NotBlank(message = "Vui lòng nhập lý do từ chối")
        @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự")
        String reason) {
}