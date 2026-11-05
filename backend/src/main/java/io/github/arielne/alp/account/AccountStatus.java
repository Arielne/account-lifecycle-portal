package io.github.arielne.alp.account;

/**
 * 5 trạng thái của tài khoản (docs/01-chuc-nang.md mục 2.1). Lần này chỉ dùng để kiểm tham số lọc của
 * GET /api/accounts: Spring đổi chữ trên URL thành enum, mã lạ thì báo 400. Việc chuyển trạng thái
 * (State pattern) làm cùng module phiếu.
 */
public enum AccountStatus {
    PENDING_OPEN,
    ACTIVE,
    BLOCKED,
    DORMANT,
    CLOSED
}