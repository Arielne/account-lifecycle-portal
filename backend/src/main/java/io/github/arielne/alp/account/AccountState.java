package io.github.arielne.alp.account;

import java.util.Set;

/**
 * State pattern cho trạng thái tài khoản (QT-02, spec phiếu mục 5). Mỗi trạng thái là một lớp riêng, tự biết mình
 * được chuyển sang những trạng thái nào theo bảng ở docs/01-chuc-nang.md mục 2.1. Muốn sửa luật chuyển của một trạng
 * thái thì chỉ sửa đúng lớp đó, không phải tìm các câu if rải rác trong Service.
 * Tạo tài khoản mới ("chưa có" → Chờ mở) không đi qua đây: đó là hàm mở tài khoản riêng của AccountService.
 */
public interface AccountState {

    /** Trạng thái mà lớp này đại diện. */
    AccountStatus status();

    /** Các trạng thái được chuyển tới từ trạng thái này. */
    Set<AccountStatus> allowedTargets();

    /** Có được chuyển sang trạng thái target không. */
    default boolean canMoveTo(AccountStatus target) {
        return allowedTargets().contains(target);
    }

    /** Lớp trạng thái ứng với mã trạng thái đang lưu trong database. */
    static AccountState of(AccountStatus status) {
        return switch (status) {
            case PENDING_OPEN -> new PendingOpenState();
            case ACTIVE -> new ActiveState();
            case BLOCKED -> new BlockedState();
            case DORMANT -> new DormantState();
            case CLOSED -> new ClosedState();
        };
    }
}