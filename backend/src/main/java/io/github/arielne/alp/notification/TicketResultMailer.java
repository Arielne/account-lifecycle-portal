package io.github.arielne.alp.notification;

import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.ticket.TicketType;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Mail kết quả phiếu (CN-24, spec phiếu mục 9 bước 3), gửi bằng Spring Mail tới Mailpit. Mail không có số CCCD.
 * Gửi lỗi (Mailpit tắt...) thì ném lỗi ra ngoài để Spring Kafka đọc lại sự kiện và gửi lại sau.
 * Tên loại phiếu và trạng thái lấy từ enum TicketType, AccountStatus, để mail ghi đúng chữ như trên màn hình.
 */
@Component
@RequiredArgsConstructor
public class TicketResultMailer {

    private final JavaMailSender mailSender;

    /** Mail cho khách: yêu cầu đã được duyệt, tài khoản hiện ở trạng thái mới. */
    public void sendApprovedToCustomer(TicketMailData ticket) {
        String body = "Kính gửi Quý khách " + ticket.getCustomerName() + ",\n\n"
                + "Yêu cầu " + typeLabel(ticket) + " của Quý khách đã được duyệt. Tài khoản "
                + ticket.getAccountNumber() + " hiện ở trạng thái " + statusLabel(ticket.getToStatus()) + ".\n"
                + "Mã yêu cầu: " + ticket.getCode() + "\n\n"
                + NotificationMail.SIGNATURE;
        send(ticket.getCustomerEmail(), "Kết quả yêu cầu " + typeLabel(ticket), body);
    }

    /** Mail cho người tạo phiếu: phiếu đã được duyệt, tài khoản đã chuyển trạng thái, khách đã nhận mail. */
    public void sendApprovedToCreator(TicketMailData ticket) {
        String body = "Chào " + ticket.getCreatorName() + ",\n\n"
                + "Phiếu " + ticket.getCode() + " (" + typeLabel(ticket) + ", tài khoản " + ticket.getAccountNumber()
                + ") đã được duyệt. Tài khoản đã chuyển " + statusLabel(ticket.getFromStatus()) + " → "
                + statusLabel(ticket.getToStatus()) + ". Hệ thống đã gửi mail báo kết quả cho khách hàng.\n\n"
                + NotificationMail.SIGNATURE;
        send(ticket.getCreatorEmail(), "Phiếu " + ticket.getCode() + " đã được duyệt", body);
    }

    /** Mail cho người tạo phiếu: phiếu bị từ chối kèm lý do, cần sửa và gửi lại hoặc hủy phiếu. */
    public void sendRejectedToCreator(TicketMailData ticket) {
        String body = "Chào " + ticket.getCreatorName() + ",\n\n"
                + "Phiếu " + ticket.getCode() + " (" + typeLabel(ticket) + ", tài khoản " + ticket.getAccountNumber()
                + ") bị từ chối với lý do:\n" + ticket.getRejectReason() + "\n\n"
                + "Vui lòng sửa và gửi lại phiếu, hoặc hủy phiếu trên Admin Portal.\n\n"
                + NotificationMail.SIGNATURE;
        send(ticket.getCreatorEmail(), "Phiếu " + ticket.getCode() + " bị từ chối", body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(NotificationMail.FROM_ADDRESS);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    /** Tên loại phiếu như trên màn: Gỡ phong tỏa cho tài khoản Ngủ đông gọi là "Kích hoạt lại". */
    private String typeLabel(TicketMailData ticket) {
        return TicketType.valueOf(ticket.getType()).labelFor(AccountStatus.valueOf(ticket.getFromStatus()));
    }

    private String statusLabel(String status) {
        return AccountStatus.valueOf(status).getLabel();
    }
}