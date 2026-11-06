package io.github.arielne.alp.ticket.workflow;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Test quy trình ticket-approval bằng camunda-bpm-assert (spec phiếu mục 13, test 23, 24, 25): đi đúng các bước của
 * nhánh duyệt, nhánh từ chối và nhánh lỗi của phiếu lô. Các service task thay bằng mock (@MockitoBean), nên test chỉ
 * kiểm đường đi của file bpmn, không đụng bảng của app. Camunda chạy thật trên Postgres của Testcontainers.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class TicketApprovalProcessIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @MockitoBean
    private ApplyAccountChangeDelegate applyAccountChangeDelegate;

    @MockitoBean
    private QueueResultMailDelegate queueResultMailDelegate;

    // Bean của module import (chưa làm): file bpmn gọi theo tên nên ở đây tạo mock đúng tên đó.
    @MockitoBean(name = "batchAccountCreator")
    private JavaDelegate batchAccountCreator;

    @BeforeEach
    void setUp() {
        // camunda-bpm-assert cần biết process engine nào để hỏi trạng thái quy trình.
        BpmnAwareTests.init(processEngine);
    }

    @Test
    @DisplayName("Nhánh duyệt: review → đổi trạng thái tài khoản → ghi outbox → chờ mailSent → correlate → kết thúc")
    void approvedSingleTicketWaitsForMailThenCompletes() throws Exception {
        // Chuẩn bị
        String ticketCode = nextCode();
        ProcessInstance process = start(ticketCode, "PHONG_TOA");
        assertThat(process).isWaitingAt("review");

        // Chạy: kiểm soát viên duyệt
        complete(task(process), withVariables("decision", "APPROVED"));

        // Kiểm tra kết quả: dừng ở bước chờ gửi mail, chưa kết thúc (T8)
        assertThat(process).hasPassed("applyAccountChange", "queueApprovedMail").isWaitingAt("mailSent");
        verify(applyAccountChangeDelegate).execute(any());
        verify(queueResultMailDelegate).execute(any());

        // Chạy: phần gửi mail báo đã gửi xong
        runtimeService.correlateMessage("mailSent", ticketCode);

        // Kiểm tra kết quả
        assertThat(process).hasPassed("mailSent", "completed").isEnded();
    }

    @Test
    @DisplayName("Nhánh từ chối: review → ghi outbox → chờ người tạo sửa (revise) → gửi lại → về review")
    void rejectedTicketGoesBackToCreatorThenReview() throws Exception {
        // Chuẩn bị
        ProcessInstance process = start(nextCode(), "DONG_TAI_KHOAN");

        // Chạy
        complete(task(process), withVariables("decision", "REJECTED"));

        // Kiểm tra kết quả: không đổi trạng thái tài khoản
        assertThat(process).hasPassed("queueRejectedMail").hasNotPassed("applyAccountChange").isWaitingAt("revise");
        verify(applyAccountChangeDelegate, never()).execute(any());

        // Chạy: người tạo sửa và gửi lại
        complete(task(process));

        // Kiểm tra kết quả
        assertThat(process).isWaitingAt("review");
        verify(queueResultMailDelegate, times(1)).execute(any());
    }

    @Test
    @DisplayName("Phiếu lô: tạo cả lô gặp dòng hỏng (BPMN error BATCH_ROWS_INVALID) thì về bước người tạo sửa")
    void batchWithInvalidRowsGoesBackToCreator() throws Exception {
        // Chuẩn bị: bean tạo lô báo có dòng hỏng
        doThrow(new BpmnError("BATCH_ROWS_INVALID")).when(batchAccountCreator).execute(any());
        ProcessInstance process = start(nextCode(), "MO_TK_THEO_LO");

        // Chạy
        complete(task(process), withVariables("decision", "APPROVED"));

        // Kiểm tra kết quả: không ghi mail duyệt, không đi nhánh phiếu lẻ
        assertThat(process).hasPassed("batchRowsInvalid").hasNotPassed("queueApprovedMail", "applyAccountChange")
                .isWaitingAt("revise");
        verify(queueResultMailDelegate, never()).execute(any());
    }

    private ProcessInstance start(String ticketCode, String ticketType) {
        return runtimeService.startProcessInstanceByKey(TicketWorkflow.PROCESS_KEY, ticketCode,
                Map.of(TicketWorkflow.TICKET_CODE_VARIABLE, ticketCode, TicketWorkflow.TICKET_TYPE_VARIABLE, ticketType));
    }

    private String nextCode() {
        return "PYC-TEST-" + String.format("%04d", SEQUENCE.getAndIncrement());
    }
}