package io.github.arielne.alp.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sự kiện theo Outbox pattern (spec phiếu mục 9, T2). Thay vì gửi mail hay gửi Kafka ngay giữa nghiệp vụ, ta
 * INSERT 1 dòng vào app.outbox_event trong cùng transaction với dữ liệu nghiệp vụ: nghiệp vụ được lưu thì sự kiện
 * cũng được lưu, nghiệp vụ bị hủy thì sự kiện cũng mất. Phần đọc bảng này rồi đẩy sang Kafka làm sau, tách riêng.
 * Đặt ở common để module nào cũng ghi được sự kiện mà không phải gọi module notification.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * MANDATORY: chỉ chạy bên trong một transaction đang mở, không có thì Spring báo lỗi ngay. Ghi outbox ngoài
     * transaction thì mất ý nghĩa "lưu cùng lúc với nghiệp vụ".
     * Payload không chứa dữ liệu cá nhân (tên, CCCD, email): ai đọc được Kafka cũng không thấy dữ liệu khách.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        outboxMapper.insert(aggregateType, aggregateId, eventType, toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            // Map chỉ chứa số và chữ nên không xảy ra; vẫn phải bắt vì đây là checked exception.
            throw new IllegalStateException("Không đổi được payload của outbox sang JSON", exception);
        }
    }
}