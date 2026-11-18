package io.github.arielne.alp.notification;

import lombok.Getter;
import lombok.Setter;

/**
 * Dữ liệu để gửi mail kết quả của một phiếu (CN-24), đọc bằng NotificationMapper.findTicketMail. Có email đầy đủ của
 * khách vì phải gửi mail tới đó; chỉ dùng ở backend, không trả xuống trình duyệt, không đưa vào Kafka.
 * rejectReason là lý do của lần từ chối gần nhất, lấy từ lịch sử xử lý (spec phiếu mục 9.1).
 * accountNumber, customerName, customerEmail rỗng với phiếu lô (chưa gắn tài khoản).
 */
@Getter
@Setter
public class TicketMailData {

    private Long id;
    private String code;
    private String type;
    private String status;
    private String fromStatus;
    private String toStatus;
    private String accountNumber;
    private String customerName;
    private String customerEmail;
    private String creatorName;
    private String creatorEmail;
    private String rejectReason;
}