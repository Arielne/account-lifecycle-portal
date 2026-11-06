package io.github.arielne.alp.ticket.workflow;

import io.github.arielne.alp.account.AccountService;
import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.ticket.TicketMapper;
import io.github.arielne.alp.ticket.TicketReviewRow;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Service task "Đổi trạng thái tài khoản" (applyAccountChange) của quy trình duyệt phiếu lẻ (spec phiếu mục 4.2).
 * File bpmn gọi bean này theo tên ${applyAccountChangeDelegate}. Chạy trong transaction của API duyệt, ngay sau khi
 * phiếu đã ghi người duyệt: đổi trạng thái tài khoản từ from_status sang to_status đã ghi lúc tạo phiếu, qua State
 * pattern của account (QT-02). Lỗi (ví dụ trạng thái tài khoản đã đổi, D7) thì cả lần duyệt bị hủy.
 */
@Component("applyAccountChangeDelegate")
@RequiredArgsConstructor
public class ApplyAccountChangeDelegate implements JavaDelegate {

    private final TicketMapper ticketMapper;
    private final AccountService accountService;

    @Override
    public void execute(DelegateExecution execution) {
        String ticketCode = (String) execution.getVariable(TicketWorkflow.TICKET_CODE_VARIABLE);
        TicketReviewRow ticket = ticketMapper.findForWorkflow(ticketCode);
        if (ticket == null) {
            throw new IllegalStateException("Quy trình trỏ tới phiếu không có: " + ticketCode);
        }
        accountService.changeStatus(ticket.getAccountId(), AccountStatus.valueOf(ticket.getFromStatus()),
                AccountStatus.valueOf(ticket.getToStatus()), ticket.getId(), ticket.getReviewedBy());
    }
}