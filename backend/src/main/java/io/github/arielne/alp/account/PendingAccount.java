package io.github.arielne.alp.account;

/** Tài khoản Chờ mở vừa tạo cho phiếu Mở tài khoản (CN-11): id, số tài khoản và id hồ sơ khách. */
public record PendingAccount(Long accountId, String accountNumber, Long customerId) {
}