package io.github.arielne.alp.account;

import java.util.Set;

/** Phong tỏa: phiếu Gỡ phong tỏa đưa về Hoạt động; phiếu Đóng tài khoản đưa sang Đã đóng. */
public class BlockedState implements AccountState {

    @Override
    public AccountStatus status() {
        return AccountStatus.BLOCKED;
    }

    @Override
    public Set<AccountStatus> allowedTargets() {
        return Set.of(AccountStatus.ACTIVE, AccountStatus.CLOSED);
    }
}