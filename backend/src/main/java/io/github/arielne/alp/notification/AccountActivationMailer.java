package io.github.arielne.alp.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Mail "Tài khoản đã được kích hoạt" gửi cho người Chờ cấp quyền vừa được gán role đầu tiên (CN-05, CN-22).
 * EmployeeEventListener gọi class này khi nhận sự kiện EMPLOYEE_ACTIVATED từ Kafka (spec phiếu mục 9.1, spec admin
 * B2); module employee không gọi thẳng notification nữa.
 * Gửi lỗi (Mailpit tắt...) thì ném lỗi ra ngoài để Spring Kafka đọc lại sự kiện và gửi lại sau, mail không bị mất.
 */
@Component
@RequiredArgsConstructor
public class AccountActivationMailer {

    private final JavaMailSender mailSender;

    public void sendAccountActivated(String email, String fullName, String roleLabels) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(NotificationMail.FROM_ADDRESS);
        message.setTo(email);
        message.setSubject("Tài khoản đã được kích hoạt");
        message.setText("Chào " + fullName + ",\n\n"
                + "Tài khoản của bạn trên Admin Portal của Ngân hàng Demo đã được cấp quyền: " + roleLabels + ".\n"
                + "Vui lòng đăng xuất rồi đăng nhập lại để bắt đầu làm việc.\n\n"
                + NotificationMail.SIGNATURE);
        mailSender.send(message);
    }
}