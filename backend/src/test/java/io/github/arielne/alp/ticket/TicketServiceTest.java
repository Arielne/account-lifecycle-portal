package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.account.AccountService;
import io.github.arielne.alp.account.PendingAccount;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Test nhanh cho nghiệp vụ tạo phiếu (spec phiếu mục 7.1, test 5 phần QT-04): chi nhánh lấy từ hồ sơ người tạo,
 * kiểm tài khoản (QT-03, trạng thái), kiểm số CCCD (D5, D6). Mapper, Storage, Camunda, AccountService là mock.
 */
class TicketServiceTest {

    private static final CurrentEmployee LAN = new CurrentEmployee(11L, "5b30439a-4e15-42ac-bf7d-c4e8dba67784",
            "Nguyễn Thị Lan", 4L, "Chi nhánh Tân Phú");
    private static final List<MultipartFile> FILES = List.of(
            new MockMultipartFile("files", "cccd.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}));

    private TicketMapper ticketMapper;
    private TicketAttachmentService attachmentService;
    private TicketWorkflow ticketWorkflow;
    private AccountService accountService;
    private CurrentEmployeeService currentEmployeeService;
    private AuditNote auditNote;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        attachmentService = mock(TicketAttachmentService.class);
        ticketWorkflow = mock(TicketWorkflow.class);
        accountService = mock(AccountService.class);
        currentEmployeeService = mock(CurrentEmployeeService.class);
        auditNote = new AuditNote();
        service = new TicketService(ticketMapper, attachmentService, ticketWorkflow, accountService,
                currentEmployeeService, auditNote);
        when(currentEmployeeService.get()).thenReturn(LAN);
        when(ticketMapper.nextCodeNumber()).thenReturn(7L);
        when(ticketWorkflow.start(any(), any())).thenReturn("process-7");
        when(attachmentService.storeAll(any())).thenReturn(
                List.of(new StoredAttachment("cccd.pdf", "uuid-1", "application/pdf", 5)));
    }

    @Test
    @DisplayName("QT-04: phiếu mang chi nhánh và người tạo lấy từ hồ sơ người đang đăng nhập; lịch sử Đã gửi")
    void ticketTakesBranchAndCreatorFromSession() {
        // Chuẩn bị: tài khoản Hoạt động, không có phiếu mở
        when(ticketMapper.findAccountByNumber("1020000050")).thenReturn(account("ACTIVE", null, null));

        // Chạy
        TicketCreated created = service.create(blockRequest(), FILES);

        // Kiểm tra kết quả
        ArgumentCaptor<TicketRecord> record = ArgumentCaptor.forClass(TicketRecord.class);
        verify(ticketMapper).insertTicket(record.capture());
        assertEquals(4L, record.getValue().getBranchId());
        assertEquals(11L, record.getValue().getCreatedBy());
        assertEquals("PENDING_APPROVAL", record.getValue().getStatus());
        assertEquals("ACTIVE", record.getValue().getFromStatus());
        assertEquals("BLOCKED", record.getValue().getToStatus());
        assertEquals("process-7", record.getValue().getProcessInstanceId());
        String expectedCode = "PYC-" + LocalDate.now().getYear() + "-0007";
        assertEquals(expectedCode, created.code());
        verify(ticketWorkflow).start(expectedCode, "PHONG_TOA");
        verify(ticketMapper).insertHistory(any(), eq("SUBMITTED"), eq(null), eq("PENDING_APPROVAL"), eq(11L),
                eq(null));
        assertEquals("Phong tỏa · TK 1020000050 · Hoạt động → Phong tỏa", auditNote.getDescription());
    }

    @Test
    @DisplayName("QT-03: tài khoản đang có phiếu chờ duyệt thì 409 kèm mã phiếu, chưa lưu file nào")
    void accountWithOpenTicketIsRejected() {
        // Chuẩn bị
        when(ticketMapper.findAccountByNumber("1020000050"))
                .thenReturn(account("ACTIVE", "PYC-2026-0003", "PENDING_APPROVAL"));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(blockRequest(), FILES));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Tài khoản đang có phiếu PYC-2026-0003 chờ duyệt", exception.getMessage());
        verify(attachmentService, never()).storeAll(any());
        verify(ticketMapper, never()).insertTicket(any());
    }

    @Test
    @DisplayName("Tài khoản Ngủ đông không tạo được phiếu Phong tỏa (409); eligibility trả allowed false kèm lý do")
    void dormantAccountCannotBeBlocked() {
        // Chuẩn bị
        when(ticketMapper.findAccountByNumber("1020000050")).thenReturn(account("DORMANT", null, null));

        // Chạy
        TicketEligibility eligibility = service.checkEligibility(TicketType.PHONG_TOA, "1020000050");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(blockRequest(), FILES));

        // Kiểm tra kết quả
        assertFalse(eligibility.allowed());
        assertEquals("Tài khoản đang Ngủ đông, không tạo được phiếu Phong tỏa", eligibility.message());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    @DisplayName("Eligibility: tài khoản Ngủ đông gỡ phong tỏa được, sẽ sang Hoạt động; CCCD đã che (QT-06)")
    void eligibilityMasksCccd() {
        // Chuẩn bị
        when(ticketMapper.findAccountByNumber("1020000050")).thenReturn(account("DORMANT", null, null));

        // Chạy
        TicketEligibility eligibility = service.checkEligibility(TicketType.GO_PHONG_TOA, "1020000050");

        // Kiểm tra kết quả
        assertTrue(eligibility.allowed());
        assertEquals("ACTIVE", eligibility.targetStatus());
        assertEquals("0991****0002", eligibility.customerCccd());
    }

    @Test
    @DisplayName("D6: khách mới có số CCCD thuộc hồ sơ chưa từng mở tài khoản thì ghi đè hồ sơ đó, audit ghi rõ")
    void reusableProfileIsOverwritten() {
        // Chuẩn bị
        when(ticketMapper.findCustomerState("099190000002", null)).thenReturn(customerState(21L, null, true));
        when(accountService.openPendingForNewCustomer(any(), eq(21L), eq(3L), eq(4L)))
                .thenReturn(new PendingAccount(50L, "1030000001", 21L));

        // Chạy
        service.create(openingRequest(), FILES);

        // Kiểm tra kết quả
        ArgumentCaptor<TicketRecord> record = ArgumentCaptor.forClass(TicketRecord.class);
        verify(ticketMapper).insertTicket(record.capture());
        assertTrue(record.getValue().isNewCustomer());
        assertEquals("PENDING_OPEN", record.getValue().getFromStatus());
        assertEquals(
                "Mở tài khoản · TK 1030000001 · Chờ mở → Hoạt động · cập nhật hồ sơ chưa từng mở tài khoản",
                auditNote.getDescription());
    }

    @Test
    @DisplayName("Khách mới mà số CCCD đã có hồ sơ thật thì 409; hồ sơ đang chờ duyệt ở phiếu khác thì 409 (D5)")
    void existingOrPendingCccdIsRejected() {
        // Chuẩn bị: lần 1 hồ sơ đã mở được tài khoản, lần 2 hồ sơ đang chờ duyệt ở phiếu khác
        when(ticketMapper.findCustomerState("099190000002", null))
                .thenReturn(customerState(21L, null, false), customerState(21L, "PYC-2026-0005", false));

        // Chạy
        BusinessException exists = assertThrows(BusinessException.class,
                () -> service.create(openingRequest(), FILES));
        BusinessException pending = assertThrows(BusinessException.class,
                () -> service.create(openingRequest(), FILES));

        // Kiểm tra kết quả
        assertEquals("Số CCCD đã có hồ sơ khách hàng. Hãy chọn Khách hàng đã có", exists.getMessage());
        assertEquals("Hồ sơ khách đang chờ duyệt ở phiếu PYC-2026-0005", pending.getMessage());
        verify(accountService, never()).openPendingForNewCustomer(any(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("customer-check trả đúng 4 trạng thái NEW, EXISTS, REUSABLE, PENDING")
    void customerCheckReturnsFourStates() {
        // Chuẩn bị
        when(ticketMapper.findCustomerState("099000000001", null)).thenReturn(null);
        when(ticketMapper.findCustomerState("099000000002", null)).thenReturn(customerState(2L, null, false));
        when(ticketMapper.findCustomerState("099000000003", null)).thenReturn(customerState(3L, null, true));
        when(ticketMapper.findCustomerState("099000000004", null)).thenReturn(customerState(4L, "PYC-2026-0009", false));

        // Chạy, kiểm tra kết quả
        assertEquals(CustomerCccdState.NEW, service.checkCustomer("099000000001").state());
        assertEquals(CustomerCccdState.EXISTS, service.checkCustomer("099000000002").state());
        assertEquals(CustomerCccdState.REUSABLE, service.checkCustomer("099000000003").state());
        CustomerCheckResult pending = service.checkCustomer("099000000004");
        assertEquals(CustomerCccdState.PENDING, pending.state());
        assertEquals("PYC-2026-0009", pending.ticketCode());
    }

    @Test
    @DisplayName("Người tạo chưa có chi nhánh thì 409, không tạo gì")
    void creatorWithoutBranchIsRejected() {
        // Chuẩn bị
        when(currentEmployeeService.get()).thenReturn(new CurrentEmployee(12L, "k-12", "Nhân Viên Mới", null, null));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(blockRequest(), FILES));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(ticketMapper, never()).insertTicket(any());
    }

    private TicketCreateRequest blockRequest() {
        return new TicketCreateRequest(TicketType.PHONG_TOA, null, null, null, "1020000050", " Khách báo mất thẻ ",
                true);
    }

    private TicketCreateRequest openingRequest() {
        NewCustomerRequest customer = new NewCustomerRequest("Đỗ Thị Bình", LocalDate.of(1990, 3, 15), "Nữ",
                "099190000002", LocalDate.of(2021, 4, 10), "Cục CS QLHC về TTXH", "0900000002",
                "binh.dt@nhdemo.local", "12 Đường Giả, Quận 1");
        return new TicketCreateRequest(TicketType.MO_TAI_KHOAN, null, customer, 3L, null, "Khách mở tài khoản",
                true);
    }

    private TicketAccountRow account(String status, String openTicketCode, String openTicketStatus) {
        TicketAccountRow row = new TicketAccountRow();
        row.setAccountId(47L);
        row.setAccountNumber("1020000050");
        row.setStatus(status);
        row.setCustomerId(21L);
        row.setCustomerName("Đỗ Thị Bình");
        row.setCustomerCccd("099190000002");
        row.setOpenTicketCode(openTicketCode);
        row.setOpenTicketStatus(openTicketStatus);
        return row;
    }

    private CustomerStateRow customerState(Long customerId, String pendingTicketCode, boolean reusable) {
        CustomerStateRow row = new CustomerStateRow();
        row.setCustomerId(customerId);
        row.setPendingTicketCode(pendingTicketCode);
        row.setReusable(reusable);
        return row;
    }
}