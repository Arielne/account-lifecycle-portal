package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.account.AccountService;
import io.github.arielne.alp.account.AccountStatus;
import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.ticket.workflow.TicketWorkflow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho sửa và gửi lại (CN-12, spec phiếu mục 7.3) và hủy phiếu (CN-13, mục 7.4): chỉ người tạo, chỉ phiếu
 * Bị từ chối; tài khoản đổi trạng thái thì 409 (D7); bỏ giấy tờ không thuộc phiếu thì 400 (D3); đổi CCCD trùng hồ sơ
 * khác thì 409 (D2); hủy thay người tạo đã bị khóa (D12). Mapper, Storage, Camunda, AccountService là mock; chạy thật
 * ở TicketRevisionIT.
 */
class TicketRevisionServiceTest {

    private static final String LAN_KEYCLOAK_ID = "5b30439a-4e15-42ac-bf7d-c4e8dba67784";
    private static final CurrentEmployee LAN = new CurrentEmployee(11L, LAN_KEYCLOAK_ID, "Nguyễn Thị Lan", 4L,
            "Chi nhánh Tân Phú");
    private static final CurrentEmployee OTHER_MAKER = new CurrentEmployee(15L, "0c9d3a52-77b1-4e0f-8d6a-2f1e5b9c4a10",
            "Giao Dịch Viên Khác", 4L, "Chi nhánh Tân Phú");

    private TicketMapper ticketMapper;
    private TicketAttachmentService attachmentService;
    private TicketWorkflow ticketWorkflow;
    private AccountService accountService;
    private CurrentEmployeeService currentEmployeeService;
    private AuditNote auditNote;
    private TicketRevisionService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        attachmentService = mock(TicketAttachmentService.class);
        ticketWorkflow = mock(TicketWorkflow.class);
        accountService = mock(AccountService.class);
        currentEmployeeService = mock(CurrentEmployeeService.class);
        auditNote = new AuditNote();
        service = new TicketRevisionService(ticketMapper, attachmentService, ticketWorkflow, accountService,
                currentEmployeeService, auditNote);
        when(currentEmployeeService.get()).thenReturn(LAN);
        when(ticketWorkflow.findReviseTaskId("process-7")).thenReturn("task-revise-7");
        when(ticketMapper.findActiveAttachments(7L)).thenReturn(List.of(attachment(3L, "don-cu.pdf"),
                attachment(4L, "cccd.pdf")));
        when(attachmentService.storeAdditional(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Gửi lại: bỏ đúng giấy tờ đã chọn, ghi tên vào lịch sử, gửi lại với lý do mới, quy trình về bước duyệt")
    void resubmitRemovesChosenAttachmentsAndCompletesRevise() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "REJECTED", "ACTIVE", "ACTIVE"));

        // Chạy
        service.resubmit(7L, request(null, List.of(3L)), List.of());

        // Kiểm tra kết quả
        ArgumentCaptor<Long[]> removedIds = ArgumentCaptor.forClass(Long[].class);
        verify(ticketMapper).removeAttachments(eq(7L), removedIds.capture(), eq(11L));
        assertArrayEquals(new Long[] {3L}, removedIds.getValue());
        verify(ticketMapper).resubmitTicket(7L, "Đã bổ sung chữ ký");
        verify(ticketMapper).insertHistory(7L, "RESUBMITTED", "REJECTED", "PENDING_APPROVAL", 11L,
                "Bỏ giấy tờ: don-cu.pdf");
        verify(ticketWorkflow).completeRevise("task-revise-7");
        assertEquals("PYC-2026-0007", auditNote.getObjectId());
    }

    @Test
    @DisplayName("Người khác sửa phiếu thì 403; phiếu không còn Bị từ chối thì 409; không ghi gì")
    void onlyCreatorEditsRejectedTicket() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "REJECTED", "ACTIVE", "ACTIVE"));
        when(ticketMapper.lockForReview(8L)).thenReturn(ticket("PHONG_TOA", "PENDING_APPROVAL", "ACTIVE", "ACTIVE"));
        when(currentEmployeeService.get()).thenReturn(OTHER_MAKER, LAN);

        // Chạy
        BusinessException notCreator = assertThrows(BusinessException.class,
                () -> service.resubmit(7L, request(null, List.of()), List.of()));
        BusinessException notRejected = assertThrows(BusinessException.class,
                () -> service.resubmit(8L, request(null, List.of()), List.of()));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.FORBIDDEN, notCreator.getStatus());
        assertEquals("Chỉ người tạo phiếu được sửa", notCreator.getMessage());
        assertEquals(HttpStatus.CONFLICT, notRejected.getStatus());
        verify(ticketMapper, never()).resubmitTicket(anyLong(), any());
    }

    @Test
    @DisplayName("D7: tài khoản đổi trạng thái trong lúc phiếu Bị từ chối thì gửi lại nhận 409, nhắc hủy và tạo phiếu mới")
    void resubmitWhenAccountStatusChangedThrowsConflict() {
        // Chuẩn bị: phiếu Phong tỏa tạo lúc Hoạt động, giờ tài khoản đã Ngủ đông
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "REJECTED", "ACTIVE", "DORMANT"));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resubmit(7L, request(null, List.of()), List.of()));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Trạng thái tài khoản đã đổi từ Hoạt động sang Ngủ đông. Hãy hủy phiếu này và tạo phiếu mới.",
                exception.getMessage());
        verify(ticketWorkflow, never()).completeRevise(any());
    }

    @Test
    @DisplayName("D3: bỏ giấy tờ không phải của phiếu này thì 400 và không bỏ gì")
    void removingForeignAttachmentIsRejected() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "REJECTED", "ACTIVE", "ACTIVE"));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resubmit(7L, request(null, List.of(99L)), List.of()));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(ticketMapper, never()).removeAttachments(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("D2: phiếu khách mới đổi sang số CCCD đã thuộc hồ sơ khác thì 409; số của chính hồ sơ mình thì sửa được")
    void newCustomerCccdMustNotBelongToAnotherProfile() {
        // Chuẩn bị: phiếu Mở tài khoản cho khách mới, hồ sơ của phiếu là khách 21
        TicketReviewRow opening = ticket("MO_TAI_KHOAN", "REJECTED", "PENDING_OPEN", "PENDING_OPEN");
        opening.setNewCustomer(true);
        when(ticketMapper.lockForReview(7L)).thenReturn(opening);
        when(ticketMapper.findCustomerState("099190000099", null)).thenReturn(customerState(30L));
        when(ticketMapper.findCustomerState("099190000002", null)).thenReturn(customerState(21L));

        // Chạy
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.resubmit(7L, request(newCustomer("099190000099"), List.of()), List.of()));
        service.resubmit(7L, request(newCustomer("099190000002"), List.of()), List.of());

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatus());
        assertEquals("Số CCCD đã có hồ sơ khách hàng", duplicate.getMessage());
        verify(accountService).revisePendingAccount(eq(47L), eq(3L), any(), eq(4L));
    }

    @Test
    @DisplayName("Hủy phiếu Mở tài khoản: tài khoản Chờ mở sang Đã đóng, phiếu Đã hủy, xóa quy trình, lịch sử kèm lý do")
    void cancelOpeningClosesPendingAccount() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("MO_TAI_KHOAN", "PENDING_APPROVAL", "PENDING_OPEN",
                "PENDING_OPEN"));

        // Chạy
        service.cancel(7L, "  Khách đổi ý  ");

        // Kiểm tra kết quả
        verify(accountService).changeStatus(47L, AccountStatus.PENDING_OPEN, AccountStatus.CLOSED, 7L, 11L);
        verify(ticketMapper).cancelTicket(7L);
        verify(ticketMapper).insertHistory(7L, "CANCELLED", "PENDING_APPROVAL", "CANCELLED", 11L, "Khách đổi ý");
        verify(ticketWorkflow).cancel("process-7");
        assertEquals("Mở tài khoản · TK 1020000050 · Chờ mở → Đã đóng · Lý do: Khách đổi ý", auditNote.getDescription());
    }

    @Test
    @DisplayName("Hủy phiếu Phong tỏa: tài khoản giữ nguyên trạng thái")
    void cancelOtherTicketKeepsAccountStatus() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "REJECTED", "ACTIVE", "ACTIVE"));

        // Chạy
        service.cancel(7L, "Khách đổi ý");

        // Kiểm tra kết quả
        verify(accountService, never()).changeStatus(any(), any(), any(), any(), any());
        verify(ticketMapper).cancelTicket(7L);
    }

    @Test
    @DisplayName("D12: người tạo đã bị khóa thì Giao dịch viên cùng chi nhánh hủy được, audit ghi hủy thay;"
            + " người tạo còn làm việc thì người khác hủy bị 403")
    void cancelOnBehalfOnlyWhenCreatorLocked() {
        // Chuẩn bị: phiếu 7 của người tạo đã bị khóa, phiếu 8 của người tạo còn làm việc
        TicketReviewRow lockedCreator = ticket("PHONG_TOA", "PENDING_APPROVAL", "ACTIVE", "ACTIVE");
        lockedCreator.setCreatorStatus("LOCKED");
        when(ticketMapper.lockForReview(7L)).thenReturn(lockedCreator);
        when(ticketMapper.lockForReview(8L)).thenReturn(ticket("PHONG_TOA", "PENDING_APPROVAL", "ACTIVE", "ACTIVE"));
        when(currentEmployeeService.get()).thenReturn(OTHER_MAKER);

        // Chạy
        service.cancel(7L, "Người tạo đã nghỉ việc");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(8L, "Thử hủy"));

        // Kiểm tra kết quả: lịch sử ghi người hủy thật
        verify(ticketMapper).insertHistory(7L, "CANCELLED", "PENDING_APPROVAL", "CANCELLED", 15L,
                "Người tạo đã nghỉ việc");
        assertEquals("Phong tỏa · TK 1020000050 · Lý do: Người tạo đã nghỉ việc · hủy thay Nguyễn Thị Lan",
                auditNote.getDescription());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Chỉ người tạo phiếu được hủy", exception.getMessage());
    }

    @Test
    @DisplayName("Hủy phiếu đã duyệt thì 409, không đổi gì")
    void cancelApprovedTicketThrowsConflict() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket("PHONG_TOA", "APPROVED", "ACTIVE", "BLOCKED"));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, "Thử hủy"));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(ticketMapper, never()).cancelTicket(anyLong());
    }

    /** Phiếu do Lan tạo ở chi nhánh 4, gắn tài khoản 47. */
    private TicketReviewRow ticket(String type, String status, String fromStatus, String accountStatus) {
        TicketReviewRow ticket = new TicketReviewRow();
        ticket.setId(7L);
        ticket.setCode("PYC-2026-0007");
        ticket.setType(type);
        ticket.setStatus(status);
        ticket.setAccountId(47L);
        ticket.setAccountNumber("1020000050");
        ticket.setCustomerId(21L);
        ticket.setAccountStatus(accountStatus);
        ticket.setBranchId(4L);
        ticket.setBranchName("Chi nhánh Tân Phú");
        ticket.setCreatedBy(11L);
        ticket.setCreatorKeycloakId(LAN_KEYCLOAK_ID);
        ticket.setCreatorName("Nguyễn Thị Lan");
        ticket.setCreatorStatus("ACTIVE");
        ticket.setProcessInstanceId("process-7");
        ticket.setFromStatus(fromStatus);
        ticket.setToStatus("BLOCKED");
        return ticket;
    }

    private TicketResubmitRequest request(NewCustomerRequest newCustomer, List<Long> removedIds) {
        Long accountTypeId = newCustomer == null ? null : 3L;
        return new TicketResubmitRequest(accountTypeId, newCustomer, "  Đã bổ sung chữ ký ", removedIds, true);
    }

    private NewCustomerRequest newCustomer(String cccd) {
        return new NewCustomerRequest("Đỗ Thị Bình", LocalDate.of(1990, 3, 15), "Nữ", cccd, LocalDate.of(2021, 4, 10),
                "Cục CS QLHC về TTXH", "0900000002", "binh.dt@nhdemo.local", "12 Đường Giả, Quận 1");
    }

    private CustomerStateRow customerState(Long customerId) {
        CustomerStateRow row = new CustomerStateRow();
        row.setCustomerId(customerId);
        return row;
    }

    private AttachmentRow attachment(Long id, String name) {
        AttachmentRow row = new AttachmentRow();
        row.setId(id);
        row.setOriginalName(name);
        row.setContentType("application/pdf");
        return row;
    }
}