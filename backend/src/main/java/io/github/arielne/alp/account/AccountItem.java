package io.github.arielne.alp.account;

import java.time.LocalDate;

/**
 * Một tài khoản trả về cho màn Tài khoản (CN-09) và Chi tiết tài khoản (CN-10); hai màn dùng chung một dạng.
 * customerCccd đã che (QT-06): record này không có trường nào chứa CCCD đầy đủ.
 * openTicket là null khi tài khoản không có phiếu đang Chờ duyệt hoặc Bị từ chối (QT-03).
 * openedByName, openApprovedByName (ô "Mở bởi", "Duyệt mở bởi") chỉ có ở chi tiết tài khoản; danh sách để null.
 */
public record AccountItem(
        Long id,
        String accountNumber,
        Long customerId,
        String customerName,
        String customerCccd,
        String accountTypeName,
        String branchName,
        String status,
        LocalDate openedAt,
        LocalDate closedAt,
        LocalDate lastTransactionAt,
        OpenTicket openTicket,
        String openedByName,
        String openApprovedByName) {

    /** Phiếu đang mở của tài khoản: mã (PYC-2026-0418), loại (PHONG_TOA) và trạng thái (PENDING_APPROVAL). */
    public record OpenTicket(String code, String type, String status) {
    }
}