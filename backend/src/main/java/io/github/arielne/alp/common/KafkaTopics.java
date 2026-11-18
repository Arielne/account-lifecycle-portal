package io.github.arielne.alp.common;

/**
 * Tên topic Kafka của app và cách chọn topic cho một sự kiện outbox (spec phiếu mục 9.1): sự kiện về phiếu vào
 * ticket-events, sự kiện về nhân viên vào employee-events. Mọi sự kiện của cùng một đối tượng vào cùng topic với cùng
 * key (id đối tượng), nên Kafka giữ đúng thứ tự của chúng.
 */
public final class KafkaTopics {

    public static final String TICKET_EVENTS = "ticket-events";
    public static final String EMPLOYEE_EVENTS = "employee-events";

    private KafkaTopics() {
    }

    /** Topic của một loại đối tượng; loại chưa có topic thì null (OutboxPublisher ghi log rồi bỏ qua dòng đó). */
    public static String forAggregate(String aggregateType) {
        if (OutboxEvents.TICKET.equals(aggregateType)) {
            return TICKET_EVENTS;
        }
        if (OutboxEvents.EMPLOYEE.equals(aggregateType)) {
            return EMPLOYEE_EVENTS;
        }
        return null;
    }
}