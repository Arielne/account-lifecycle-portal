package io.github.arielne.alp.notification;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.KafkaTopics;
import io.github.arielne.alp.common.OutboxEvents;
import io.github.arielne.alp.common.OutboxMessage;
import io.github.arielne.alp.ticket.TicketService;
import io.github.arielne.alp.ticket.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * Nhận sự kiện về phiếu từ Kafka (topic ticket-events, group alp-notification trong application.yml) và gửi mail kết
 * quả (CN-24, spec phiếu mục 9 bước 3, mục 9.1).
 * @KafkaListener: Spring Kafka tự đọc topic trong một luồng riêng và gọi method này cho từng message, lần lượt theo
 * thứ tự. Method ném lỗi thì Spring Kafka đọc lại đúng message đó và gọi lại sau (NotificationKafkaConfig).
 * Kafka giao "ít nhất một lần": phiếu đã Hoàn tất thì bỏ qua sự kiện duyệt tới lần hai, không gửi mail lần nữa.
 * notification chỉ đọc bảng của module khác (NotificationMapper) và gọi TicketService để ghi kết quả (CLAUDE.md).
 */
@Component
@RequiredArgsConstructor
public class TicketEventListener {

    private static final Logger log = LoggerFactory.getLogger(TicketEventListener.class);

    private final OutboxMessageReader messageReader;
    private final NotificationMapper notificationMapper;
    private final TicketResultMailer ticketResultMailer;
    private final TicketService ticketService;

    @KafkaListener(topics = KafkaTopics.TICKET_EVENTS)
    public void onTicketEvent(String message) {
        OutboxMessage event = messageReader.read(KafkaTopics.TICKET_EVENTS, message);
        if (event == null) {
            return;
        }
        long ticketId = event.payload().path("ticketId").asLong();
        TicketMailData ticket = notificationMapper.findTicketMail(ticketId);
        if (ticket == null) {
            log.warn("Sự kiện {} trỏ tới phiếu {} không có, bỏ qua", event.eventType(), ticketId);
            return;
        }
        if (ticket.getAccountNumber() == null) {
            log.info("Phiếu lô {} gửi mail theo spec import theo lô (chưa làm), bỏ qua", ticket.getCode());
            return;
        }
        try {
            handle(event.eventType(), ticket);
        } catch (MailException exception) {
            log.warn("Chưa gửi được mail của phiếu {}, Spring Kafka sẽ thử lại: {}", ticket.getCode(),
                    exception.getMessage());
            throw exception;
        }
    }

    private void handle(String eventType, TicketMailData ticket) {
        if (OutboxEvents.TICKET_APPROVED.equals(eventType)) {
            handleApproved(ticket);
            return;
        }
        if (OutboxEvents.TICKET_REJECTED.equals(eventType)) {
            // Phiếu giữ Bị từ chối; không có gì để ghi thêm (spec phiếu mục 9 bước 3).
            ticketResultMailer.sendRejectedToCreator(ticket);
            return;
        }
        log.warn("Loại sự kiện {} của phiếu {} chưa có cách xử lý, bỏ qua", eventType, ticket.getCode());
    }

    /**
     * Phiếu được duyệt: gửi mail cho khách và người tạo, rồi nhờ ticket chuyển phiếu sang Hoàn tất. Phiếu không còn
     * Đã duyệt (đã Hoàn tất vì sự kiện tới lần hai) thì bỏ qua, không gửi mail. markMailSent trả 409 (phiếu vừa đổi
     * trạng thái) thì cũng bỏ qua: thử lại không làm phiếu quay về Đã duyệt được.
     */
    private void handleApproved(TicketMailData ticket) {
        if (!TicketStatus.APPROVED.name().equals(ticket.getStatus())) {
            log.info("Phiếu {} không còn Đã duyệt, bỏ qua sự kiện duyệt tới lần nữa", ticket.getCode());
            return;
        }
        ticketResultMailer.sendApprovedToCustomer(ticket);
        ticketResultMailer.sendApprovedToCreator(ticket);
        try {
            ticketService.markMailSent(ticket.getId());
        } catch (BusinessException exception) {
            log.info("Không chuyển được phiếu {} sang Hoàn tất: {}", ticket.getCode(), exception.getMessage());
        }
    }
}