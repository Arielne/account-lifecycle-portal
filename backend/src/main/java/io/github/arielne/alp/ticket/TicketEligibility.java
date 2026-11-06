package io.github.arielne.alp.ticket;

/**
 * Kết quả GET /api/tickets/eligibility (CN-11, bước 2 của màn Tạo phiếu): thẻ tài khoản (CCCD đã che, QT-06),
 * trạng thái trước → sau nếu phiếu được duyệt, và tài khoản có tạo được phiếu loại này không.
 * allowed false thì message nói lý do, ví dụ "Tài khoản đang có phiếu PYC-2026-0007 chờ duyệt".
 */
public record TicketEligibility(Long accountId, String accountNumber, Long customerId, String customerName,
                                String customerCccd, String accountTypeName, String branchName,
                                String currentStatus, String targetStatus, boolean allowed, String message) {
}