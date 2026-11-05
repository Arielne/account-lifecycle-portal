package io.github.arielne.alp.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho AccountService (spec khách hàng tài khoản mục 7, test 4 và 5): che CCCD của chủ tài khoản,
 * đổi tham số lọc thành AccountFilter, không có tài khoản thì 404. AccountMapper là mock; câu SQL test ở AccountMapperIT.
 */
class AccountServiceTest {

    private AccountMapper accountMapper;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        service = new AccountService(accountMapper);
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