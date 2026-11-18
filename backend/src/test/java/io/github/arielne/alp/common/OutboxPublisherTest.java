package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Test nhanh cho OutboxPublisher (spec phiếu mục 9 bước 2, mục 9.1): chọn topic theo loại đối tượng, message dạng
 * {"eventType", "payload"}, Kafka xác nhận rồi mới đánh dấu đã gửi, Kafka lỗi thì dừng lượt. OutboxMapper và
 * KafkaTemplate là mock: CI không có Kafka.
 * Kafka xác nhận được giả bằng CompletableFuture.completedFuture (kết quả có sẵn), Kafka lỗi bằng failedFuture.
 */
class OutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxMapper outboxMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher publisher;

    // mock(KafkaTemplate.class) trả kiểu không có <String, String>; Java cảnh báo "unchecked" nên tắt cảnh báo ở đây.
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new OutboxPublisher(outboxMapper, kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("Sự kiện phiếu vào ticket-events, sự kiện nhân viên vào employee-events, key là id đối tượng;"
            + " Kafka xác nhận thì đánh dấu đã gửi")
    void sendsEachEventToTopicOfItsAggregate() throws Exception {
        // Chuẩn bị
        when(outboxMapper.lockUnpublished(50)).thenReturn(List.of(
                event(1L, "TICKET", "5", "TICKET_APPROVED", "{\"ticketId\": 5, \"ticketCode\": \"PYC-2026-0005\"}"),
                event(2L, "EMPLOYEE", "7", "EMPLOYEE_ACTIVATED", "{\"employeeId\": 7}")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Chạy
        publisher.publishPendingEvents();

        // Kiểm tra kết quả: message có loại sự kiện và payload giữ nguyên
        ArgumentCaptor<String> ticketMessage = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("ticket-events"), eq("5"), ticketMessage.capture());
        JsonNode message = objectMapper.readTree(ticketMessage.getValue());
        assertEquals("TICKET_APPROVED", message.path("eventType").asText());
        assertEquals(5, message.path("payload").path("ticketId").asLong());
        assertEquals("PYC-2026-0005", message.path("payload").path("ticketCode").asText());
        verify(kafkaTemplate).send(eq("employee-events"), eq("7"), anyString());
        verify(outboxMapper).markPublished(1L);
        verify(outboxMapper).markPublished(2L);
    }

    @Test
    @DisplayName("Kafka lỗi ở dòng thứ hai thì dừng lượt: dòng đầu đã đánh dấu, dòng lỗi và dòng sau giữ nguyên")
    void stopsTheRoundAtTheFirstKafkaFailure() {
        // Chuẩn bị
        when(outboxMapper.lockUnpublished(50)).thenReturn(List.of(
                event(1L, "TICKET", "5", "TICKET_APPROVED", "{\"ticketId\": 5}"),
                event(2L, "TICKET", "6", "TICKET_REJECTED", "{\"ticketId\": 6}"),
                event(3L, "TICKET", "7", "TICKET_APPROVED", "{\"ticketId\": 7}")));
        when(kafkaTemplate.send(eq("ticket-events"), eq("5"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("ticket-events"), eq("6"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("Kafka không trả lời")));

        // Chạy
        publisher.publishPendingEvents();

        // Kiểm tra kết quả
        verify(outboxMapper).markPublished(1L);
        verify(outboxMapper, never()).markPublished(2L);
        verify(kafkaTemplate, never()).send(eq("ticket-events"), eq("7"), anyString());
        verify(outboxMapper, never()).markPublished(3L);
    }

    @Test
    @DisplayName("Producer báo lỗi ngay lúc gửi (không nối được Kafka) cũng là chưa gửi, không đánh dấu")
    void errorAtSendTimeLeavesEventUnpublished() {
        // Chuẩn bị
        when(outboxMapper.lockUnpublished(50)).thenReturn(List.of(
                event(1L, "TICKET", "5", "TICKET_APPROVED", "{\"ticketId\": 5}")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Không lấy được thông tin topic"));

        // Chạy
        publisher.publishPendingEvents();

        // Kiểm tra kết quả
        verify(outboxMapper, never()).markPublished(anyLong());
    }

    @Test
    @DisplayName("Loại đối tượng chưa có topic thì bỏ qua dòng đó, không đánh dấu, các dòng sau vẫn gửi")
    void skipsEventWithoutTopic() {
        // Chuẩn bị
        when(outboxMapper.lockUnpublished(50)).thenReturn(List.of(
                event(1L, "IMPORT_BATCH", "3", "BATCH_DONE", "{\"batchId\": 3}"),
                event(2L, "TICKET", "5", "TICKET_APPROVED", "{\"ticketId\": 5}")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Chạy
        publisher.publishPendingEvents();

        // Kiểm tra kết quả
        verify(outboxMapper, never()).markPublished(1L);
        verify(kafkaTemplate).send(eq("ticket-events"), eq("5"), anyString());
        verify(outboxMapper).markPublished(2L);
    }

    private OutboxEvent event(Long id, String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        return event;
    }
}