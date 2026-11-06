package io.github.arielne.alp.ticket.workflow;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

/**
 * Chỗ duy nhất module phiếu gọi Camunda (spec phiếu, mục 4): bắt đầu quy trình ticket-approval cho phiếu mới.
 * Camunda dùng chung transaction với app, nên quy trình chỉ được lưu khi phiếu được lưu; lỗi thì cả hai cùng bị hủy.
 * Biến quy trình chỉ có mã và loại phiếu, không có dữ liệu khách hàng.
 */
@Component
@RequiredArgsConstructor
public class TicketWorkflow {

    /** Id của process trong file bpmn/ticket-approval.bpmn. */
    public static final String PROCESS_KEY = "ticket-approval";
    public static final String TICKET_CODE_VARIABLE = "ticketCode";
    public static final String TICKET_TYPE_VARIABLE = "ticketType";

    private final RuntimeService runtimeService;

    /**
     * Bắt đầu quy trình với business key là mã phiếu; quy trình dừng ngay ở bước "Kiểm soát viên duyệt" (review).
     * Trả id của process instance để lưu vào cột ticket.process_instance_id.
     */
    public String start(String ticketCode, String ticketType) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(TICKET_CODE_VARIABLE, ticketCode);
        variables.put(TICKET_TYPE_VARIABLE, ticketType);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, ticketCode, variables);
        return processInstance.getId();
    }
}