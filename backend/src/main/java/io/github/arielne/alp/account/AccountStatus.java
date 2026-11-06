package io.github.arielne.alp.account;

/**
 * 5 trạng thái của tài khoản (docs/01-chuc-nang.md mục 2.1), kèm nhãn tiếng Việt cho câu báo lỗi và audit.
 * Dùng để kiểm tham số lọc của GET /api/accounts (Spring đổi chữ trên URL thành enum, mã lạ thì báo 400) và để
 * phiếu biết trạng thái nào được tạo loại phiếu nào. Việc chuyển trạng thái (State pattern) làm ở bước duyệt phiếu.
 */
public enum AccountStatus {
    PENDING_OPEN("Chờ mở"),
    ACTIVE("Hoạt động"),
    BLOCKED("Phong tỏa"),
    DORMANT("Ngủ đông"),
    CLOSED("Đã đóng");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}