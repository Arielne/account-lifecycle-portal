package io.github.arielne.alp.common;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nội dung một message trên Kafka (spec phiếu mục 9.1), dạng JSON {"eventType": "...", "payload": {...}}.
 * OutboxPublisher ghép từ cột event_type và payload của outbox; notification đọc lại để biết phải gửi mail nào.
 * JsonNode: một giá trị JSON bất kỳ của Jackson, vì mỗi loại sự kiện có payload khác nhau.
 */
public record OutboxMessage(String eventType, JsonNode payload) {
}