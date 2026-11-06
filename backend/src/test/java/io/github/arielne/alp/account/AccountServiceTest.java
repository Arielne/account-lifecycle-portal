package io.github.arielne.alp.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.customer.CustomerProfile;
import io.github.arielne.alp.customer.CustomerService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho AccountService (spec khách hàng tài khoản mục 7, test 4 và 5): che CCCD của chủ tài khoản,
 * đổi tham số lọc thành AccountFilter, không có tài khoản thì 404; mở tài khoản Chờ mở cho phiếu (spec phiếu,
 * mục 5). AccountMapper, CustomerService là mock; câu SQL test ở AccountMapperIT.
 */
class AccountServiceTest {

    private AccountMapper accountMapper;
    private CustomerService customerService;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        customerService = mock(CustomerService.class);
        service = new AccountService(accountMapper, customerService);
    }

    @Test
    @DisplayName("Mở tài khoản cho khách mới có hồ sơ cũ chưa từng mở tài khoản: ghi đè hồ sơ đó, không tạo hồ sơ mới (D6)")
    void openPendingForReusableCustomerOverwritesProfile() {
        // Chuẩn bị: loại tài khoản có thật; database sinh id 50 và số 1030000001 cho tài khoản mới
        when(accountMapper.accountTypeExists(3L)).thenReturn(true);
        when(accountMapper.insertPending(any())).thenAnswer(invocation -> {
            AccountRecord record = invocation.getArgument(0);
            record.setId(50L);
            record.setAccountNumber("1030000001");
            return 1;
        });
        CustomerProfile profile = new CustomerProfile("Đỗ Thị Bình", LocalDate.of(1990, 3, 15), "Nữ",
                "099190000002", LocalDate.of(2021, 4, 10), "Cục CS QLHC về TTXH", "0900000002",
                "binh.dt@nhdemo.local", "12 Đường Giả, Quận 1");

        // Chạy: hồ sơ id 21 là hồ sơ cũ dùng lại được
        PendingAccount account = service.openPendingForNewCustomer(profile, 21L, 3L, 4L);

        // Kiểm tra kết quả
        verify(customerService).overwrite(21L, profile, 4L);
        verify(customerService, never()).create(any(), any());
        assertEquals(new PendingAccount(50L, "1030000001", 21L), account);
    }

    @Test
    @DisplayName("Mở tài khoản với loại tài khoản không có thì 404, không thêm tài khoản")
    void openPendingWithMissingAccountTypeThrowsNotFound() {
        // Chuẩn bị
        when(accountMapper.accountTypeExists(99L)).thenReturn(false);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.openPendingForCustomer(21L, 99L, 4L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(accountMapper, never()).insertPending(any());
    }

    @Test
    @DisplayName("Trang tài khoản trả CCCD đã che, không có số đầy đủ (QT-06); có phiếu đang mở thì kèm mã phiếu")
    void findPageMasksCccdAndMapsOpenTicket() {
        // Chuẩn bị: một tài khoản có phiếu Phong tỏa đang chờ duyệt, một tài khoản không có phiếu
        AccountRow withTicket = row(1L, "1099000001");
        withTicket.setOpenTicketCode("PYC-2026-0418");
        withTicket.setOpenTicketType("PHONG_TOA");
        withTicket.setOpenTicketStatus("PENDING_APPROVAL");
        AccountRow withoutTicket = row(2L, "1099000002");
        when(accountMapper.findPage(any())).thenReturn(List.of(withTicket, withoutTicket));
        when(accountMapper.count(any())).thenReturn(2L);

        // Chạy
        AccountPage page = service.findPage(new AccountQuery(null, null, null, null, null, null));

        // Kiểm tra kết quả
        assertEquals(2, page.total());
        AccountItem first = page.items().get(0);
        assertEquals("0990****5678", first.customerCccd());
        assertEquals(new AccountItem.OpenTicket("PYC-2026-0418", "PHONG_TOA", "PENDING_APPROVAL"), first.openTicket());
        assertNull(page.items().get(1).openTicket());
    }

    @Test
    @DisplayName("Tham số lọc thành AccountFilter: trạng thái thành mảng mã, trang 3 bỏ qua 20 dòng")
    void queryIsConvertedToFilter() {
        // Chuẩn bị
        AccountQuery query = new AccountQuery("1099", List.of(AccountStatus.ACTIVE, AccountStatus.DORMANT), 5L, 6L,
                7L, 3);

        // Chạy
        service.findPage(query);

        // Kiểm tra kết quả
        AccountFilter filter = capturedFilter();
        assertEquals("1099", filter.getAccountNumberPrefix());
        assertArrayEquals(new String[] {"ACTIVE", "DORMANT"}, filter.getStatuses());
        assertEquals(5L, filter.getAccountTypeId());
        assertEquals(6L, filter.getBranchId());
        assertEquals(7L, filter.getCustomerId());
        assertEquals(20L, filter.getOffset());
        assertEquals(10, filter.getSize());
    }

    @Test
    @DisplayName("Không chọn trạng thái nào thì không lọc trạng thái; trang nhỏ hơn 1 tính là trang 1")
    void emptyStatusesAndSmallPageMeanNoFilterAndFirstPage() {
        // Chuẩn bị
        AccountQuery query = new AccountQuery(null, List.of(), null, null, null, 0);

        // Chạy
        service.findPage(query);

        // Kiểm tra kết quả
        AccountFilter filter = capturedFilter();
        assertNull(filter.getStatuses());
        assertEquals(0L, filter.getOffset());
    }

    @Test
    @DisplayName("Xem chi tiết tài khoản không có thì báo 404")
    void findByIdOfMissingAccountThrowsNotFound() {
        // Chuẩn bị: mapper không tìm thấy
        when(accountMapper.findById(99L)).thenReturn(null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.findById(99L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Không tìm thấy tài khoản", exception.getMessage());
    }

    private AccountFilter capturedFilter() {
        ArgumentCaptor<AccountFilter> filterCaptor = ArgumentCaptor.forClass(AccountFilter.class);
        verify(accountMapper).findPage(filterCaptor.capture());
        return filterCaptor.getValue();
    }

    private AccountRow row(Long id, String accountNumber) {
        AccountRow row = new AccountRow();
        row.setId(id);
        row.setAccountNumber(accountNumber);
        row.setCustomerId(1L);
        row.setCustomerName("Phạm Văn An");
        row.setCustomerCccd("099012345678");
        row.setAccountTypeName("Thanh toán cá nhân");
        row.setBranchName("Chi nhánh Tân Phú");
        row.setStatus("ACTIVE");
        row.setOpenedAt(LocalDate.of(2024, 1, 10));
        row.setLastTransactionAt(LocalDate.of(2026, 10, 1));
        return row;
    }
}