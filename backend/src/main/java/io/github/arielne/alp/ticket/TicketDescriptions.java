package io.github.arielne.alp.ticket;

import io.github.arielne.alp.account.AccountStatus;

/**
 * Câu mô tả phiếu theo lời nghiệp vụ cho audit log (spec phiếu mục 12), dùng chung cho tạo, duyệt, từ chối phiếu.
 * Ví dụ "Phong tỏa · TK 1024118603 · Hoạt động → Phong tỏa".
 */
final class TicketDescriptions {

    /** Lý do trong mô tả audit cắt còn chừng này ký tự; lý do đầy đủ nằm ở lịch sử xử lý của phiếu. */
    private static final int REASON_MAX_LENGTH = 100;

    private TicketDescriptions() {
    }

    static String accountChange(TicketType type, String accountNumber, AccountStatus from, AccountStatus to) {
        return type.labelFor(from) + " · TK " + accountNumber + " · " + from.getLabel() + " → " + to.getLabel();
    }

    /** Thêm " · Lý do: ..." vào cuối mô tả, lý do dài thì cắt và thêm dấu "…". */
    static String withReason(String description, String reason) {
        String shortReason = reason;
        if (reason.length() > REASON_MAX_LENGTH) {
            shortReason = reason.substring(0, REASON_MAX_LENGTH) + "…";
        }
        return description + " · Lý do: " + shortReason;
    }
}