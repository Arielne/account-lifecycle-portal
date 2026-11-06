package io.github.arielne.alp.account;

import java.util.Set;

/** Đã đóng: trạng thái cuối, chỉ xem, không mở lại được nên không chuyển đi đâu. */
public class ClosedState implements AccountState {

    @Override
    public AccountStatus status() {
        return AccountStatus.CLOSED;
    }

    @Override
    public Set<AccountStatus> allowedTargets() {
        return Set.of();
    }
}