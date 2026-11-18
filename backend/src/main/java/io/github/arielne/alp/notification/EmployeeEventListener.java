package io.github.arielne.alp.notification;

import io.github.arielne.alp.common.KafkaTopics;
import io.github.arielne.alp.common.OutboxEvents;
import io.github.arielne.alp.common.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * Nhận sự kiện về nhân viên từ Kafka (topic employee-events) và gửi mail "Tài khoản đã được kích hoạt" (CN-05,
 * CN-22, spec phiếu mục 9.1). Cách đọc topic, thử lại khi gửi lỗi giống TicketEventListener.
 * Kafka giao "ít nhất một lần": backend tắt giữa chừng thì mail có thể gửi 2 lần; chấp nhận như mail kết quả phiếu.
 */
@Component
@RequiredArgsConstructor
public class EmployeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    private final OutboxMessageReader messageReader;
    private final NotificationMapper notificationMapper;
    private final AccountActivationMailer accountActivationMailer;

    @KafkaListener(topics = KafkaTopics.EMPLOYEE_EVENTS)
    public void onEmployeeEvent(String message) {
        OutboxMessage event = messageReader.read(KafkaTopics.EMPLOYEE_EVENTS, message);
        if (event == null) {
            return;
        }
        if (!OutboxEvents.EMPLOYEE_ACTIVATED.equals(event.eventType())) {
            log.warn("Loại sự kiện {} của nhân viên chưa có cách xử lý, bỏ qua", event.eventType());
            return;
        }
        long employeeId = event.payload().path("employeeId").asLong();
        EmployeeMailData employee = notificationMapper.findEmployeeMail(employeeId);
        if (employee == null) {
            log.warn("Sự kiện kích hoạt trỏ tới nhân viên {} không có, bỏ qua", employeeId);
            return;
        }
        try {
            accountActivationMailer.sendAccountActivated(employee.getEmail(), employee.getFullName(),
                    event.payload().path("roleLabels").asText());
        } catch (MailException exception) {
            log.warn("Chưa gửi được mail kích hoạt cho nhân viên {}, Spring Kafka sẽ thử lại: {}", employeeId,
                    exception.getMessage());
            throw exception;
        }
    }
}