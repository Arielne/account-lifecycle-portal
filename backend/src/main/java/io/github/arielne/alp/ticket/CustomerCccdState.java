package io.github.arielne.alp.ticket;

/**
 * Trạng thái của một số CCCD khi tạo phiếu Mở tài khoản (spec phiếu, mục 7.1), dùng cho customer-check và bước
 * kiểm trước khi tạo phiếu:
 * - NEW: chưa có hồ sơ, phiếu tạo hồ sơ mới.
 * - EXISTS: đã có hồ sơ, phải chọn "Khách hàng đã có".
 * - REUSABLE: hồ sơ chưa từng mở được tài khoản (mọi tài khoản Đã đóng, chưa bao giờ Hoạt động); phiếu mới ghi đè (D6).
 * - PENDING: hồ sơ đang chờ duyệt ở một phiếu Mở tài khoản khác (D5).
 */
public enum CustomerCccdState {
    NEW,
    EXISTS,
    REUSABLE,
    PENDING
}