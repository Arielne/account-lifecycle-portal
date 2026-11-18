package io.github.arielne.alp.common;

/**
 * Tên loại đối tượng và loại sự kiện ghi vào bảng outbox_event (spec phiếu mục 9, 9.1). Đặt ở common để module ghi
 * sự kiện (ticket, employee) và module nhận sự kiện (notification) dùng chung một tên, không ai gõ lại chuỗi riêng.
 */
public final class OutboxEvents {

    /** aggregate_type của sự kiện về phiếu; aggregate_id là id phiếu. */
    public static final String TICKET = "TICKET";
    /** aggregate_type của sự kiện về nhân viên; aggregate_id là id nhân viên. */
    public static final String EMPLOYEE = "EMPLOYEE";

    /** Phiếu vừa được duyệt: gửi mail cho khách và người tạo, gửi xong thì phiếu sang Hoàn tất. */
    public static final String TICKET_APPROVED = "TICKET_APPROVED";
    /** Phiếu vừa bị từ chối: gửi mail kèm lý do cho người tạo. */
    public static final String TICKET_REJECTED = "TICKET_REJECTED";
    /** Nhân viên Chờ cấp quyền vừa được gán role đầu tiên: gửi mail "Tài khoản đã được kích hoạt" (CN-05). */
    public static final String EMPLOYEE_ACTIVATED = "EMPLOYEE_ACTIVATED";

    private OutboxEvents() {
    }
}