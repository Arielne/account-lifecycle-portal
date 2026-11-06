package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh cho cờ nút Duyệt, Từ chối ở chi tiết phiếu (spec phiếu mục 8.3): người tạo thấy nút khóa kèm lý do
 * (QT-01, lớp chặn thứ hai), kiểm soát viên khác chi nhánh không thấy nút (D11), giao dịch viên không có nút.
 */
class TicketQueryServiceTest {

    private static final String LAN_KEYCLOAK_ID = "5b30439a-4e15-42ac-bf7d-c4e8dba67784";
    private static final CurrentEmployee KHOA = new CurrentEmployee(12L, "8f0c2d1e-1b7a-4f5e-9a63-2d4b8e7c1f00",
            "Trần Minh Khoa", 4L, "Chi nhánh Tân Phú");

    private TicketMapper ticketMapper;
    private CurrentEmployeeService currentEmployeeService;
    private TicketQueryService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        currentEmployeeService = mock(CurrentEmployeeService.class);
        service = new TicketQueryService(ticketMapper, mock(TicketAttachmentService.class), currentEmployeeService);
        when(ticketMapper.findActiveAttachments(7L)).thenReturn(List.of());
        when(ticketMapper.findHistory(7L)).thenReturn(List.of());
        when(currentEmployeeService.get()).thenReturn(KHOA);
        when(currentEmployeeService.currentUserHasRole("CHECKER")).thenReturn(true);
    }

    @Test
    @DisplayName("Kiểm soát viên cùng chi nhánh, không phải người tạo: được Duyệt, Từ chối")
    void sameBranchCheckerCanReview() {
        // Chuẩn bị
        when(ticketMapper.findDetail(7L)).thenReturn(row("PENDING_APPROVAL", LAN_KEYCLOAK_ID, 4L));

        // Chạy
        TicketDetail detail = service.findDetail(7L);

        // Kiểm tra kết quả
        assertTrue(detail.canApprove());
        assertTrue(detail.canReject());
        assertNull(detail.blockedReason());
    }

    @Test
    @DisplayName("QT-01: người tạo phiếu (giữ cả CHECKER) thấy nút bị khóa kèm lý do")
    void creatorSeesDisabledButtonsWithReason() {
        // Chuẩn bị
        when(ticketMapper.findDetail(7L)).thenReturn(row("PENDING_APPROVAL", KHOA.keycloakId(), 4L));

        // Chạy
        TicketDetail detail = service.findDetail(7L);

        // Kiểm tra kết quả
        assertFalse(detail.canApprove());
        assertEquals("Bạn không thể duyệt phiếu do chính mình tạo", detail.blockedReason());
    }

    @Test
    @DisplayName("D11: kiểm soát viên khác chi nhánh không có nút và không có lý do khóa")
    void otherBranchCheckerHasNoButtons() {
        // Chuẩn bị
        when(ticketMapper.findDetail(7L)).thenReturn(row("PENDING_APPROVAL", LAN_KEYCLOAK_ID, 5L));

        // Chạy
        TicketDetail detail = service.findDetail(7L);

        // Kiểm tra kết quả
        assertFalse(detail.canApprove());
        assertNull(detail.blockedReason());
    }

    @Test
    @DisplayName("Giao dịch viên, hoặc phiếu không còn Chờ duyệt: không có nút Duyệt, Từ chối")
    void makerOrHandledTicketHasNoButtons() {
        // Chuẩn bị: lần đầu người xem không có role CHECKER; lần sau phiếu đã duyệt
        when(ticketMapper.findDetail(7L)).thenReturn(row("PENDING_APPROVAL", LAN_KEYCLOAK_ID, 4L),
                row("APPROVED", LAN_KEYCLOAK_ID, 4L));
        when(currentEmployeeService.currentUserHasRole("CHECKER")).thenReturn(false, true);

        // Chạy
        TicketDetail asMaker = service.findDetail(7L);
        TicketDetail approved = service.findDetail(7L);

        // Kiểm tra kết quả
        assertFalse(asMaker.canApprove());
        assertFalse(approved.canApprove());
        assertNull(approved.blockedReason());
    }

    private TicketDetailRow row(String status, String creatorKeycloakId, Long branchId) {
        TicketDetailRow row = new TicketDetailRow();
        row.setId(7L);
        row.setCode("PYC-2026-0007");
        row.setType("PHONG_TOA");
        row.setStatus(status);
        row.setCustomerCccd("099190000002");
        row.setCreatorKeycloakId(creatorKeycloakId);
        row.setBranchId(branchId);
        row.setBranchName("Chi nhánh Tân Phú");
        row.setSubmitCount(1);
        return row;
    }
}