package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.ticket.workflow.TicketWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho duyệt, từ chối và màn Chờ duyệt (spec phiếu mục 8, test 5): người duyệt là người tạo thì 403 (QT-01),
 * khác chi nhánh thì 403 (QT-04), phiếu không còn Chờ duyệt thì 409 (QT-11). Mapper, Camunda là mock; chạy thật trên
 * database và Camunda ở TicketReviewIT.
 */
class TicketReviewServiceTest {

    private static final String LAN_KEYCLOAK_ID = "5b30439a-4e15-42ac-bf7d-c4e8dba67784";
    private static final CurrentEmployee KHOA = new CurrentEmployee(12L, "8f0c2d1e-1b7a-4f5e-9a63-2d4b8e7c1f00",
            "Trần Minh Khoa", 4L, "Chi nhánh Tân Phú");

    private TicketMapper ticketMapper;
    private TicketWorkflow ticketWorkflow;
    private CurrentEmployeeService currentEmployeeService;
    private AuditNote auditNote;
    private TicketReviewService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        ticketWorkflow = mock(TicketWorkflow.class);
        currentEmployeeService = mock(CurrentEmployeeService.class);
        auditNote = new AuditNote();
        service = new TicketReviewService(ticketMapper, ticketWorkflow, currentEmployeeService, auditNote);
        when(currentEmployeeService.get()).thenReturn(KHOA);
        when(ticketWorkflow.findReviewTaskId("process-7")).thenReturn("task-7");
    }

    @Test
    @DisplayName("Duyệt: ghi Đã duyệt và lịch sử trước, rồi mới hoàn thành bước duyệt của Camunda; audit mô tả thay đổi")
    void approveUpdatesTicketBeforeCompletingTask() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(pendingTicket(LAN_KEYCLOAK_ID, 4L));

        // Chạy
        service.approve(7L);

        // Kiểm tra kết quả: service task đọc người duyệt từ phiếu nên phiếu phải được ghi trước
        InOrder order = inOrder(ticketMapper, ticketWorkflow);
        order.verify(ticketMapper).updateReview(7L, "APPROVED", 12L, null);
        order.verify(ticketMapper).insertHistory(7L, "APPROVED", "PENDING_APPROVAL", "APPROVED", 12L, null);
        order.verify(ticketWorkflow).completeReview("task-7", "APPROVED");
        assertEquals("PYC-2026-0007", auditNote.getObjectId());
        assertEquals("Phong tỏa · TK 1020000050 · Hoạt động → Phong tỏa", auditNote.getDescription());
    }

    @Test
    @DisplayName("QT-01: người duyệt là người tạo phiếu thì 403, kể cả khi người đó giữ cả MAKER và CHECKER")
    void creatorCannotReviewOwnTicket() {
        // Chuẩn bị: phiếu do chính Khoa tạo (so keycloak_id, không so role)
        when(ticketMapper.lockForReview(7L)).thenReturn(pendingTicket(KHOA.keycloakId(), 4L));

        // Chạy
        BusinessException approveError = assertThrows(BusinessException.class, () -> service.approve(7L));
        BusinessException rejectError = assertThrows(BusinessException.class, () -> service.reject(7L, "Sai"));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.FORBIDDEN, approveError.getStatus());
        assertEquals("Bạn không thể duyệt phiếu do chính mình tạo", approveError.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, rejectError.getStatus());
        verify(ticketWorkflow, never()).completeReview(anyString(), anyString());
        verify(ticketMapper, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    @DisplayName("QT-04: kiểm soát viên khác chi nhánh với phiếu thì 403, nói rõ chi nhánh được duyệt")
    void reviewerFromOtherBranchIsForbidden() {
        // Chuẩn bị: phiếu của chi nhánh 5 (Quận 1), Khoa ở chi nhánh 4
        TicketReviewRow ticket = pendingTicket(LAN_KEYCLOAK_ID, 5L);
        ticket.setBranchName("Chi nhánh Quận 1");
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(7L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Chỉ kiểm soát viên Chi nhánh Quận 1 được duyệt phiếu này", exception.getMessage());
        verify(ticketWorkflow, never()).completeReview(anyString(), anyString());
    }

    @Test
    @DisplayName("QT-11: phiếu đã được người khác duyệt (không còn Chờ duyệt) thì 409, không ghi gì")
    void ticketAlreadyHandledReturnsConflict() {
        // Chuẩn bị
        TicketReviewRow ticket = pendingTicket(LAN_KEYCLOAK_ID, 4L);
        ticket.setStatus("APPROVED");
        when(ticketMapper.lockForReview(7L)).thenReturn(ticket);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(7L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Phiếu này vừa được người khác xử lý", exception.getMessage());
        verify(ticketMapper, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    @DisplayName("QT-11: quy trình không còn ở bước duyệt thì 409 như phiếu đã được xử lý")
    void missingReviewTaskReturnsConflict() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(pendingTicket(LAN_KEYCLOAK_ID, 4L));
        when(ticketWorkflow.findReviewTaskId("process-7")).thenReturn(null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.reject(7L, "Sai"));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(ticketMapper, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Phiếu không có thì 404")
    void missingTicketReturnsNotFound() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(99L)).thenReturn(null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(99L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    @DisplayName("Từ chối: lý do bỏ khoảng trắng hai đầu rồi lưu; audit kèm lý do cắt còn 100 ký tự")
    void rejectTrimsReasonAndShortensAuditText() {
        // Chuẩn bị
        when(ticketMapper.lockForReview(7L)).thenReturn(pendingTicket(LAN_KEYCLOAK_ID, 4L));
        String longReason = "  " + "a".repeat(120) + "  ";

        // Chạy
        service.reject(7L, longReason);

        // Kiểm tra kết quả
        String trimmed = "a".repeat(120);
        verify(ticketMapper).updateReview(7L, "REJECTED", 12L, trimmed);
        verify(ticketMapper).insertHistory(7L, "REJECTED", "PENDING_APPROVAL", "REJECTED", 12L, trimmed);
        verify(ticketWorkflow).completeReview("task-7", "REJECTED");
        assertEquals("Phong tỏa · TK 1020000050 · Hoạt động → Phong tỏa · Lý do: " + "a".repeat(100) + "…",
                auditNote.getDescription());
    }

    @Test
    @DisplayName("Chờ duyệt: chi nhánh và người xem lấy từ session (QT-04); mã gõ thường đổi sang hoa, ô trống không lọc")
    void pendingReviewUsesViewerBranchAndNormalizesCode() {
        // Chuẩn bị, chạy
        service.findPendingReview("  pyc-2026 ", TicketType.PHONG_TOA);
        service.findPendingReview("   ", null);

        // Kiểm tra kết quả
        ArgumentCaptor<PendingReviewFilter> filters = ArgumentCaptor.forClass(PendingReviewFilter.class);
        verify(ticketMapper, times(2)).findPendingReview(filters.capture());
        PendingReviewFilter first = filters.getAllValues().get(0);
        assertEquals(4L, first.getBranchId());
        assertEquals(12L, first.getViewerId());
        assertEquals("PYC-2026", first.getCodePrefix());
        assertEquals("PHONG_TOA", first.getType());
        PendingReviewFilter second = filters.getAllValues().get(1);
        assertNull(second.getCodePrefix());
        assertNull(second.getType());
    }

    /** Phiếu Phong tỏa Chờ duyệt, tạo bởi người có keycloakId, thuộc chi nhánh branchId. */
    private TicketReviewRow pendingTicket(String creatorKeycloakId, Long branchId) {
        TicketReviewRow ticket = new TicketReviewRow();
        ticket.setId(7L);
        ticket.setCode("PYC-2026-0007");
        ticket.setType("PHONG_TOA");
        ticket.setStatus("PENDING_APPROVAL");
        ticket.setAccountId(47L);
        ticket.setAccountNumber("1020000050");
        ticket.setBranchId(branchId);
        ticket.setBranchName("Chi nhánh Tân Phú");
        ticket.setCreatedBy(11L);
        ticket.setCreatorKeycloakId(creatorKeycloakId);
        ticket.setProcessInstanceId("process-7");
        ticket.setFromStatus("ACTIVE");
        ticket.setToStatus("BLOCKED");
        return ticket;
    }
}