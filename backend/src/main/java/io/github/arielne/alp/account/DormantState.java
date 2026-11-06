package io.github.arielne.alp.account;

import java.util.Set;

/** Ngủ đông: phiếu Kích hoạt lại đưa về Hoạt động; phiếu Đóng tài khoản đưa sang Đã đóng. */
public class DormantState implements AccountState {

    @Override
    public AccountStatus status() {
        return AccountStatus.DORMANT;
    }

    @Override
    public Set<AccountStatus> allowedTargets() {
        return Set.of(AccountStatus.ACTIVE, AccountStatus.CLOSED);
    }
}