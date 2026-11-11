package io.github.arielne.alp.account;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một mục của Vòng đời tài khoản (GET /api/accounts/{id}/status-history, CN-10, spec phiếu mục 10.6), dùng làm cả
 * kết quả MyBatis lẫn JSON vì không có dữ liệu phải che. fromStatus rỗng: tài khoản vừa được tạo; actorName rỗng:
 * Hệ thống (job Ngủ đông hoặc dữ liệu mẫu); ticket* rỗng khi không theo phiếu nào. ticketFromStatus để React gọi đúng
 * tên "Kích hoạt lại"; dormantAfterMonths cho ghi chú "Không phát sinh giao dịch trong <n> tháng".
 */
@Getter
@Setter
public class AccountStatusHistoryItem {

    private Long id;
    private OffsetDateTime changedAt;
    private String fromStatus;
    private String toStatus;
    private String actorName;
    private Long ticketId;
    private String ticketCode;
    private String ticketType;
    private String ticketFromStatus;
    private String reason;
    private int dormantAfterMonths;
}