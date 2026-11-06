package io.github.arielne.alp.ticket;

/**
 * Kết quả kiểm số CCCD (CN-11): customerId có giá trị khi đã có hồ sơ; ticketCode là mã phiếu đang giữ hồ sơ khi
 * state là PENDING. Không trả họ tên hay CCCD: React tự gọi API khách hàng (đã che dữ liệu) khi cần hiện thẻ khách.
 */
public record CustomerCheckResult(CustomerCccdState state, Long customerId, String ticketCode) {
}