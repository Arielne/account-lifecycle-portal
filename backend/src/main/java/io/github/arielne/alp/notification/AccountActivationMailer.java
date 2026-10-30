package io.github.arielne.alp.notification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Mail "Tài khoản đã được kích hoạt" gửi cho người Chờ cấp quyền vừa được gán role đầu tiên (CN-05, CN-22).
 * TẠM THỜI: module employee gọi thẳng class này và mail gửi ngay bằng Spring Mail tới Mailpit (spec admin, B2).
 * Sau sẽ đổi sang Outbox + Kafka như các mail khác, đúng quy tắc "không module nào gọi thẳng notification".
 */
@Component
@RequiredArgsConstructor
public class AccountActivationMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountActivationMailer.class);
    private static final String FROM_ADDRESS = "no-reply@nhdemo.local";

    private final JavaMailSender mailSender;

    /**
     * Gửi mail kích hoạt. Gửi lỗi (Mailpit tắt...) thì chỉ ghi log, không làm hỏng thao tác gán role:
     * role đã ghi vào KeyCloak rồi, và mail chỉ là thông báo.
     */
    public void sendAccountActivated(String email, String fullName, String roleLabels) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_ADDRESS);
        message.setTo(email);
        message.setSubject("Tài khoản đã được kích hoạt");
        message.setText("Chào " + fullName + ",\n\n"
                + "Tài khoản của bạn trên Admin Portal của Ngân hàng Demo đã được cấp quyền: " + roleLabels + ".\n"
                + "Vui lòng đăng xuất rồi đăng nhập lại để bắt đầu làm việc.\n\n"
                + "Ngân hàng Demo");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Không gửi được mail kích hoạt tới {}: {}", email, exception.getMessage());
        }
    }
}