package io.github.arielne.alp.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.BusinessException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho CustomerService (spec khách hàng tài khoản mục 7, test 3 đến 5): chuẩn hóa từ khóa, CCCD phải đủ
 * 12 số, che CCCD, điện thoại, email (QT-06), gom trạng thái tài khoản theo khách. CustomerMapper là mock.
 */
class CustomerServiceTest {

    private CustomerMapper customerMapper;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        customerMapper = mock(CustomerMapper.class);
        service = new CustomerService(customerMapper);
    }

    @Test
    @DisplayName("Tìm theo họ tên: bỏ khoảng trắng thừa, đưa chữ có dấu về dạng NFC; trang rỗng thì không hỏi tài khoản")
    void nameKeywordIsNormalized() {
        // Chuẩn bị: từ khóa ở dạng NFD (dấu tách rời chữ cái) và có khoảng trắng thừa
        String decomposedKeyword = "  " + Normalizer.normalize("Đỗ   Thị", Normalizer.Form.NFD) + " ";
        when(customerMapper.findPage(any())).thenReturn(List.of());

        // Chạy
        CustomerPage page = service.search(new CustomerSearchRequest(CustomerSearchBy.NAME, decomposedKeyword, null));

        // Kiểm tra kết quả
        CustomerFilter filter = capturedFilter();
        assertEquals("Đỗ Thị", filter.getNameKeyword());
        assertNull(filter.getCccd());
        assertEquals(0, page.items().size());
        verify(customerMapper, never()).findAccountStatuses(any());
    }

    @Test
    @DisplayName("Tìm theo CCCD chưa đủ 12 số thì báo 400, không hỏi database")
    void cccdWithElevenDigitsThrowsBadRequest() {
        // Chuẩn bị
        CustomerSearchRequest request = new CustomerSearchRequest(CustomerSearchBy.CCCD, "09900000001", null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.search(request));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Số CCCD phải đủ 12 chữ số", exception.getMessage());
        verify(customerMapper, never()).findPage(any());
    }

    @Test
    @DisplayName("Tìm theo CCCD có khoảng trắng hai đầu: bỏ khoảng trắng rồi tìm khớp tuyệt đối; trang 3 bỏ qua 20 dòng")
    void cccdIsTrimmedAndPageBecomesOffset() {
        // Chuẩn bị
        when(customerMapper.findPage(any())).thenReturn(List.of());

        // Chạy
        service.search(new CustomerSearchRequest(CustomerSearchBy.CCCD, " 099000000001 ", 3));

        // Kiểm tra kết quả
        CustomerFilter filter = capturedFilter();
        assertEquals("099000000001", filter.getCccd());
        assertNull(filter.getNameKeyword());
        assertEquals(20L, filter.getOffset());
    }

    @Test
    @DisplayName("Danh sách: CCCD, điện thoại đã che; trạng thái tài khoản gom đúng khách; ngày tạo theo giờ Việt Nam")
    void listMasksDataAndGroupsAccountStatuses() {
        // Chuẩn bị: khách 1 có 2 tài khoản, khách 2 chưa có tài khoản nào; hồ sơ tạo lúc 1 giờ sáng 1/11 giờ Việt Nam
        when(customerMapper.findPage(any())).thenReturn(List.of(row(1L), row(2L)));
        when(customerMapper.findAccountStatuses(any()))
                .thenReturn(List.of(accountStatus(1L, "ACTIVE"), accountStatus(1L, "DORMANT")));
        when(customerMapper.count(any())).thenReturn(2L);

        // Chạy
        CustomerPage page = service.search(new CustomerSearchRequest(CustomerSearchBy.NAME, null, null));

        // Kiểm tra kết quả
        CustomerListItem first = page.items().get(0);
        assertEquals("0990****0001", first.cccd());
        assertEquals("090****001", first.phone());
        assertEquals(List.of("ACTIVE", "DORMANT"), first.accountStatuses());
        assertEquals(LocalDate.of(2026, 11, 1), first.createdDate());
        assertEquals(List.of(), page.items().get(1).accountStatuses());
        assertEquals(2, page.total());
    }

    @Test
    @DisplayName("Chi tiết khách: CCCD, điện thoại, email đều đã che")
    void detailMasksCccdPhoneAndEmail() {
        // Chuẩn bị
        when(customerMapper.findById(1L)).thenReturn(row(1L));

        // Chạy
        CustomerDetail detail = service.findById(1L);

        // Kiểm tra kết quả
        assertEquals("0990****0001", detail.cccd());
        assertEquals("090****001", detail.phone());
        assertEquals("an.***@nhdemo.local", detail.email());
        assertEquals("Chi nhánh Tân Phú", detail.branchName());
    }

    @Test
    @DisplayName("Xem chi tiết khách không có thì báo 404")
    void missingCustomerThrowsNotFound() {
        // Chuẩn bị
        when(customerMapper.findById(99L)).thenReturn(null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.findById(99L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Không tìm thấy khách hàng", exception.getMessage());
    }

    private CustomerFilter capturedFilter() {
        ArgumentCaptor<CustomerFilter> filterCaptor = ArgumentCaptor.forClass(CustomerFilter.class);
        verify(customerMapper).findPage(filterCaptor.capture());
        return filterCaptor.getValue();
    }

    private CustomerRow row(Long id) {
        CustomerRow row = new CustomerRow();
        row.setId(id);
        row.setCccd("099000000001");
        row.setFullName("Phạm Văn An");
        row.setDateOfBirth(LocalDate.of(1985, 2, 8));
        row.setPhone("0900000001");
        row.setEmail("an.pham@nhdemo.local");
        row.setBranchName("Chi nhánh Tân Phú");
        row.setCreatedAt(OffsetDateTime.parse("2026-10-31T18:00:00Z"));
        return row;
    }

    private CustomerAccountRow accountStatus(Long customerId, String status) {
        CustomerAccountRow accountRow = new CustomerAccountRow();
        accountRow.setCustomerId(customerId);
        accountRow.setStatus(status);
        return accountRow;
    }
}