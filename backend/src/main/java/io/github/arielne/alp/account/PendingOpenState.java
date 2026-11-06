package io.github.arielne.alp.account;

import java.util.Set;

/** Chờ mở: phiếu Mở tài khoản được duyệt thì sang Hoạt động; người tạo hủy phiếu thì sang Đã đóng. */
public class PendingOpenState implements AccountState {

    @Override
    public AccountStatus status() {
        return AccountStatus.PENDING_OPEN;
    }

    @Override
    public Set<AccountStatus> allowedTargets() {
        return Set.of(AccountStatus.ACTIVE, AccountStatus.CLOSED);
    }
}