package io.github.arielne.alp.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đẩy sự kiện của bảng outbox_event sang Kafka (Outbox pattern, CN-24, spec phiếu mục 9 bước 2). Nghiệp vụ chỉ ghi
 * outbox trong transaction của mình (OutboxWriter); class này chạy riêng 2 giây một lần: đọc các dòng chưa gửi, gửi
 * sang Kafka, Kafka xác nhận rồi mới đánh dấu đã gửi. Kafka tắt thì dòng giữ nguyên, lượt sau gửi lại.
 * Kafka giao "ít nhất một lần": backend tắt đúng lúc Kafka đã nhận mà chưa kịp đánh dấu thì lượt sau gửi lại lần nữa,
 * nên phía nhận (notification) phải chịu được một sự kiện tới hai lần.
 * KafkaTemplate: lớp của Spring Kafka để gửi message, key và value đều là chuỗi.
 * @Scheduled(fixedDelay): lượt sau bắt đầu 2 giây sau khi lượt trước xong, hai lượt không bao giờ chồng lên nhau.
 * @ConditionalOnProperty: alp.outbox.publisher-enabled=false thì không tạo bean này (test không có Kafka).
 */
@Component
@ConditionalOnProperty(name = "alp.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    // Chờ Kafka xác nhận lâu hơn delivery.timeout.ms (application.yml): Kafka lỗi thì producer tự báo lỗi trước.
    private static final long SEND_TIMEOUT_SECONDS = 15;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Một lượt gửi, trong 1 transaction: lấy tối đa 50 dòng chưa gửi (cũ nhất trước) và khóa chúng, gửi từng dòng,
     * dòng nào gửi được thì đánh dấu dòng đó. Kafka lỗi ở dòng nào thì dừng lượt ở dòng đó: Kafka đang tắt thì các dòng
     * sau cũng không gửi được, và dừng lại thì sự kiện vẫn đi đúng thứ tự.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxMapper.lockUnpublished(BATCH_SIZE)) {
            String topic = KafkaTopics.forAggregate(event.getAggregateType());
            if (topic == null) {
                log.error("Sự kiện outbox {} có loại đối tượng {} chưa có topic, bỏ qua", event.getId(),
                        event.getAggregateType());
                continue;
            }
            if (!send(topic, event)) {
                return;
            }
            outboxMapper.markPublished(event.getId());
        }
    }

    /**
     * Gửi một sự kiện, key là id đối tượng, rồi đợi Kafka xác nhận; trả false khi Kafka lỗi (đã ghi log cảnh báo).
     * Không nối được Kafka thì producer có thể báo lỗi ngay lúc gửi (RuntimeException) hoặc sau đó qua kết quả chờ
     * (ExecutionException, TimeoutException); cả ba đều là "chưa gửi được".
     */
    private boolean send(String topic, OutboxEvent event) {
        String message = toMessage(event);
        try {
            kafkaTemplate.send(topic, event.getAggregateId(), message).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            log.warn("Chưa gửi được sự kiện outbox {} sang Kafka, lượt sau gửi lại: {}", event.getId(),
                    exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            // Backend đang tắt: dừng lượt, dòng chưa gửi giữ nguyên cho lần chạy sau.
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Message dạng {"eventType": ..., "payload": ...}; payload là cột payload giữ nguyên (spec phiếu mục 9.1). */
    private String toMessage(OutboxEvent event) {
        try {
            OutboxMessage message = new OutboxMessage(event.getEventType(), objectMapper.readTree(event.getPayload()));
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            // Cột payload kiểu jsonb nên luôn là JSON hợp lệ; vẫn phải bắt vì đây là checked exception.
            throw new IllegalStateException("Payload của sự kiện outbox " + event.getId() + " không phải JSON",
                    exception);
        }
    }
}