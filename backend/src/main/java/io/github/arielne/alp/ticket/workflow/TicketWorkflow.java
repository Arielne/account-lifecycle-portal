package io.github.arielne.alp.ticket.workflow;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Component;

/**
 * Chỗ duy nhất module phiếu gọi Camunda (spec phiếu, mục 4): bắt đầu quy trình ticket-approval cho phiếu mới, tìm
 * và hoàn thành bước "Kiểm soát viên duyệt", bước "Giao dịch viên sửa", và xóa quy trình khi hủy phiếu.
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
    /** Kết quả của bước duyệt: APPROVED hoặc REJECTED; gateway decisionGateway đọc biến này để chọn nhánh. */
    public static final String DECISION_VARIABLE = "decision";
    /** Id của user task "Kiểm soát viên duyệt" trong file bpmn. */
    public static final String REVIEW_TASK = "review";
    /** Id của user task "Giao dịch viên sửa" (phiếu Bị từ chối chờ ở đây). */
    public static final String REVISE_TASK = "revise";

    private final RuntimeService runtimeService;
    private final TaskService taskService;

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

    /** Id của task "Kiểm soát viên duyệt" đang chờ trong quy trình của phiếu; quy trình không ở bước đó thì null. */
    public String findReviewTaskId(String processInstanceId) {
        return findTaskId(processInstanceId, REVIEW_TASK);
    }

    /** Id của task "Giao dịch viên sửa" đang chờ; quy trình không ở bước đó thì null. */
    public String findReviseTaskId(String processInstanceId) {
        return findTaskId(processInstanceId, REVISE_TASK);
    }

    /** Người tạo sửa xong và gửi lại: hoàn thành bước sửa, quy trình quay về bước duyệt (spec phiếu mục 4.2). */
    public void completeRevise(String taskId) {
        taskService.complete(taskId);
    }

    /**
     * Hủy phiếu (CN-13): xóa process instance đang dừng ở bước duyệt hoặc bước sửa. Lịch sử của Camunda vẫn giữ lại
     * (history-level audit); chạy chung transaction nên hủy phiếu bị lỗi thì quy trình cũng không bị xóa.
     */
    public void cancel(String processInstanceId) {
        runtimeService.deleteProcessInstance(processInstanceId, "Phiếu bị hủy");
    }

    private String findTaskId(String processInstanceId, String taskDefinitionKey) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        if (task == null) {
            return null;
        }
        return task.getId();
    }

    /**
     * Hoàn thành bước duyệt với kết quả decision (APPROVED hoặc REJECTED). Camunda chạy tiếp ngay trong thread này và
     * trong transaction đang mở: các service task (đổi trạng thái tài khoản, ghi outbox) chạy xong rồi hàm mới trả
     * về. Service task ném lỗi thì lỗi đi ra tới đây và cả transaction bị hủy, task vẫn còn ở bước duyệt.
     */
    public void completeReview(String taskId, String decision) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(DECISION_VARIABLE, decision);
        taskService.complete(taskId, variables);
    }
}