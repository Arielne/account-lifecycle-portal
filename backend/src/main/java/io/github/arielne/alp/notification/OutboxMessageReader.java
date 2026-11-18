package io.github.arielne.alp.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Đọc một message Kafka dạng {"eventType": ..., "payload": ...} (spec phiếu mục 9.1), dùng chung cho 2 listener của
 * notification. Message hỏng thì ghi log và trả null để listener bỏ qua: đọc lại bao nhiêu lần cũng vẫn hỏng, thử lại
 * chỉ chặn các sự kiện xếp sau.
 */
@Component
@RequiredArgsConstructor
public class OutboxMessageReader {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessageReader.class);

    private final ObjectMapper objectMapper;

    public OutboxMessage read(String topic, String message) {
        try {
            OutboxMessage event = objectMapper.readValue(message, OutboxMessage.class);
            if (event.eventType() == null || event.payload() == null) {
                log.error("Message trên topic {} thiếu eventType hoặc payload, bỏ qua", topic);
                return null;
            }
            return event;
        } catch (JsonProcessingException exception) {
            log.error("Message trên topic {} không phải JSON đúng dạng, bỏ qua: {}", topic, exception.getMessage());
            return null;
        }
    }
}