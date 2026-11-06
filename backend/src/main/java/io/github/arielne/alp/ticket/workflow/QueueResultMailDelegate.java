package io.github.arielne.alp.ticket.workflow;

import io.github.arielne.alp.common.OutboxWriter;
import io.github.arielne.alp.ticket.TicketMapper;
import io.github.arielne.alp.ticket.TicketReviewRow;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Service task "Gửi mail kết quả" (queueApprovedMail, queueRejectedMail) của quy trình duyệt phiếu (spec phiếu mục
 * 4.2, 9). File bpmn gọi bean này theo tên ${queueResultMailDelegate}. Không gửi mail ở đây: chỉ ghi 1 dòng outbox
 * trong transaction của API duyệt, từ chối. Mail đi sau khi transaction đã lưu, qua Kafka (làm ở bước sau của module
 * phiếu); nhờ vậy máy gửi mail chậm hay tắt không làm API duyệt lỗi, và lần duyệt bị hủy thì không có mail nào.
 */
@Component("queueResultMailDelegate")
@RequiredArgsConstructor
public class QueueResultMailDelegate implements JavaDelegate {

    public static final String AGGREGATE_TYPE = "TICKET";
    public static final String TICKET_APPROVED_EVENT = "TICKET_APPROVED";
    public static final String TICKET_REJECTED_EVENT = "TICKET_REJECTED";

    private final TicketMapper ticketMapper;
    private final OutboxWriter outboxWriter;

    @Override
    public void execute(DelegateExecution execution) {
        String ticketCode = (String) execution.getVariable(TicketWorkflow.TICKET_CODE_VARIABLE);
        String decision = (String) execution.getVariable(TicketWorkflow.DECISION_VARIABLE);
        TicketReviewRow ticket = ticketMapper.findForWorkflow(ticketCode);
        if (ticket == null) {
            throw new IllegalStateException("Quy trình trỏ tới phiếu không có: " + ticketCode);
        }
        String eventType = TICKET_REJECTED_EVENT;
        if ("APPROVED".equals(decision)) {
            eventType = TICKET_APPROVED_EVENT;
        }
        // Payload chỉ có id và mã phiếu; phần gửi mail tự đọc phiếu khi cần, không đưa dữ liệu khách vào Kafka.
        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketCode", ticketCode);
        outboxWriter.write(AGGREGATE_TYPE, String.valueOf(ticket.getId()), eventType, payload);
    }
}