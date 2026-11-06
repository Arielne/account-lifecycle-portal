package io.github.arielne.alp.ticket;

import io.github.arielne.alp.account.AccountStatus;
import java.util.List;

/**
 * 5 loại phiếu (docs/01-chuc-nang.md mục 2.2), kèm trạng thái tài khoản được phép tạo phiếu và trạng thái sau khi
 * duyệt (spec phiếu, bảng ở mục 5). Phiếu Mở tài khoản không có tài khoản sẵn: tài khoản Chờ mở được tạo cùng phiếu.
 * Phiếu lô (MO_TK_THEO_LO) làm ở module import, không tạo qua API phiếu lẻ.
 */
public enum TicketType {
    MO_TAI_KHOAN("Mở tài khoản", List.of(), AccountStatus.ACTIVE),
    PHONG_TOA("Phong tỏa", List.of(AccountStatus.ACTIVE), AccountStatus.BLOCKED),
    GO_PHONG_TOA("Gỡ phong tỏa", List.of(AccountStatus.BLOCKED, AccountStatus.DORMANT), AccountStatus.ACTIVE),
    DONG_TAI_KHOAN("Đóng tài khoản",
            List.of(AccountStatus.ACTIVE, AccountStatus.BLOCKED, AccountStatus.DORMANT), AccountStatus.CLOSED),
    MO_TK_THEO_LO("Mở tài khoản theo lô", List.of(), AccountStatus.ACTIVE);

    private final String label;
    private final List<AccountStatus> allowedFromStatuses;
    private final AccountStatus targetStatus;

    TicketType(String label, List<AccountStatus> allowedFromStatuses, AccountStatus targetStatus) {
        this.label = label;
        this.allowedFromStatuses = allowedFromStatuses;
        this.targetStatus = targetStatus;
    }

    public AccountStatus getTargetStatus() {
        return targetStatus;
    }

    /** Phiếu này gắn với một tài khoản đã có (Phong tỏa, Gỡ phong tỏa, Đóng tài khoản). */
    public boolean needsExistingAccount() {
        return !allowedFromStatuses.isEmpty();
    }

    public boolean allowsFrom(AccountStatus status) {
        return allowedFromStatuses.contains(status);
    }

    /** Tên loại phiếu; Gỡ phong tỏa cho tài khoản Ngủ đông gọi là "Kích hoạt lại" (spec phiếu, mục 10.5). */
    public String labelFor(AccountStatus fromStatus) {
        if (this == GO_PHONG_TOA && fromStatus == AccountStatus.DORMANT) {
            return "Kích hoạt lại";
        }
        return label;
    }
}