package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.auth.CurrentEmployee;
import io.github.arielne.alp.auth.CurrentEmployeeService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test nhanh cho chi tiết phiếu và Tất cả phiếu: cờ nút Duyệt, Từ chối (spec phiếu mục 8.3; người tạo thấy nút khóa
 * kèm lý do QT-01, kiểm soát viên khác chi nhánh không thấy nút D11), cờ Sửa, Hủy (mục 10.5, D12), và cách đổi bộ
 * lọc Tất cả phiếu (khoảng ngày theo giờ Việt Nam, số đếm theo trạng thái D8).
 */
class TicketQueryServiceTest {

    private static final String LAN_KEYCLOAK_ID = "5b30439a-4e15-42ac-bf7d-c4e8dba67784";
    private static final CurrentEmployee KHOA = new CurrentEmployee(12L, "8f0c2d1e-1b7a-4f5e-9a63-2d4b8e7c1f00",
            "Trần Minh Khoa", 4L, "Chi nhánh Tân Phú");
    private static final CurrentEmployee LAN = new CurrentEmployee(11L, LAN_KEYCLOAK_ID, "Nguyễn Thị Lan", 4L,
            "Chi nhánh Tân Phú");
    private static final CurrentEmployee OTHER_MAKER = new CurrentEmployee(15L, "0c9d3a52-77b1-4e0f-8d6a-2f1e5b9c4a10",
            "Giao Dịch Viên Khác", 4L, "Chi nhánh Tân Phú");

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

    @Test
    @DisplayName("Người tạo (Giao dịch viên) xem phiếu Bị từ chối: được Sửa và gửi lại, được Hủy, không phải hủy thay")
    void creatorCanEditAndCancelRejectedTicket() {
        // Chuẩn bị: người xem là Lan, người tạo phiếu
        when(currentEmployeeService.get()).thenReturn(LAN);
        when(currentEmployeeService.currentUserHasRole("MAKER")).thenReturn(true);
        when(ticketMapper.findDetail(7L)).thenReturn(row("REJECTED", LAN_KEYCLOAK_ID, 4L));

        // Chạy
        TicketDetail detail = service.findDetail(7L);

        // Kiểm tra kết quả
        assertTrue(detail.canEdit());
        assertTrue(detail.canCancel());
        assertFalse(detail.cancelForLockedCreator());
    }

    @Test
    @DisplayName("D12: người tạo đã bị khóa thì Giao dịch viên cùng chi nhánh thấy Hủy (hủy thay) nhưng không thấy Sửa")
    void sameBranchMakerCancelsForLockedCreator() {
        // Chuẩn bị: người xem là Giao dịch viên khác cùng chi nhánh 4
        when(currentEmployeeService.get()).thenReturn(OTHER_MAKER);
        when(currentEmployeeService.currentUserHasRole("MAKER")).thenReturn(true);
        TicketDetailRow lockedCreator = row("PENDING_APPROVAL", LAN_KEYCLOAK_ID, 4L);
        lockedCreator.setCreatorStatus("LOCKED");
        when(ticketMapper.findDetail(7L)).thenReturn(lockedCreator);

        // Chạy
        TicketDetail detail = service.findDetail(7L);

        // Kiểm tra kết quả
        assertFalse(detail.canEdit());
        assertTrue(detail.canCancel());
        assertTrue(detail.cancelForLockedCreator());
    }

    @Test
    @DisplayName("Tất cả phiếu: ngày lọc theo giờ Việt Nam, mã gõ thường đổi sang hoa; đếm đủ 5 trạng thái, thiếu thì 0")
    void listFilterUsesVietnamDaysAndFillsStatusCounts() {
        // Chuẩn bị
        TicketStatusCount pending = new TicketStatusCount();
        pending.setStatus("PENDING_APPROVAL");
        pending.setCount(3);
        when(ticketMapper.countByStatus(any())).thenReturn(List.of(pending));
        TicketListQuery query = new TicketListQuery(List.of(TicketStatus.REJECTED), " pyc-2026", null, null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), null, null, 2);

        // Chạy
        TicketListPage page = service.findPage(query);

        // Kiểm tra kết quả: 0 giờ ngày 1/11 giờ Việt Nam là 17 giờ ngày 31/10 giờ UTC; mốc cuối là 0 giờ ngày 8/11
        ArgumentCaptor<TicketListFilter> filter = ArgumentCaptor.forClass(TicketListFilter.class);
        verify(ticketMapper).findPage(filter.capture());
        assertEquals(OffsetDateTime.parse("2026-10-31T17:00:00Z").toInstant(),
                filter.getValue().getCreatedFrom().toInstant());
        assertEquals(OffsetDateTime.parse("2026-11-07T17:00:00Z").toInstant(),
                filter.getValue().getCreatedBefore().toInstant());
        assertEquals("PYC-2026", filter.getValue().getCodePrefix());
        assertArrayEquals(new String[] {"REJECTED"}, filter.getValue().getStatuses());
        assertEquals(10L, filter.getValue().getOffset());
        assertEquals(List.of("PENDING_APPROVAL", "REJECTED", "APPROVED", "COMPLETED", "CANCELLED"),
                List.copyOf(page.statusCounts().keySet()));
        assertEquals(3L, page.statusCounts().get("PENDING_APPROVAL"));
        assertEquals(0L, page.statusCounts().get("CANCELLED"));
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