package io.github.arielne.alp.account;

import java.util.Set;

/**
 * Hoạt động: sang Phong tỏa (phiếu Phong tỏa), Đã đóng (phiếu Đóng tài khoản), hoặc Ngủ đông. Ngủ đông chỉ do job ban
 * đêm chuyển (CN-25, chạy trong database), không có loại phiếu nào nhắm tới Ngủ đông.
 */
public class ActiveState implements AccountState {

    @Override
    public AccountStatus status() {
        return AccountStatus.ACTIVE;
    }

    @Override
    public Set<AccountStatus> allowedTargets() {
        return Set.of(AccountStatus.BLOCKED, AccountStatus.DORMANT, AccountStatus.CLOSED);
    }
}