package io.github.arielne.alp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho nghiệp vụ loại tài khoản (spec admin mục 8, test 2–3): mã trùng, không đổi mã, không xóa khi đang dùng.
 * Không dựng Spring: AccountTypeMapper là mock của Mockito, AuditNote là object thường.
 */
class AccountTypeServiceTest {

    private AccountTypeMapper accountTypeMapper;
    private AuditNote auditNote;
    private AccountTypeService accountTypeService;

    @BeforeEach
    void setUp() {
        accountTypeMapper = mock(AccountTypeMapper.class);
        auditNote = new AuditNote();
        accountTypeService = new AccountTypeService(accountTypeMapper, auditNote);
    }

    @Test
    @DisplayName("Thêm loại tài khoản trùng mã thì báo 409, không thêm dòng nào")
    void createWithDuplicateCodeThrowsConflict() {
        // Chuẩn bị: mã TKL đã có
        when(accountTypeMapper.existsByCode("TKL")).thenReturn(true);
        AccountTypeCreateRequest request = new AccountTypeCreateRequest("TKL", "Tài khoản lương", null, 12);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> accountTypeService.create(request));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Mã loại TKL đã tồn tại", exception.getMessage());
        verify(accountTypeMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Sửa loại tài khoản thì mã giữ nguyên; giảm số tháng vẫn lưu và audit ghi trước, sau")
    void updateKeepsCodeAndDescribesMonthChange() {
        // Chuẩn bị: loại TKL đang để 18 tháng
        when(accountTypeMapper.findById(2L)).thenReturn(accountType("TKL", "Tài khoản lương", 18, 612));
        AccountTypeUpdateRequest request = new AccountTypeUpdateRequest("Tài khoản lương", null, 12);

        // Chạy
        accountTypeService.update(2L, request);

        // Kiểm tra kết quả: ArgumentCaptor bắt object được truyền vào mapper.update để xem bên trong
        ArgumentCaptor<AccountType> savedCaptor = ArgumentCaptor.forClass(AccountType.class);
        verify(accountTypeMapper).update(savedCaptor.capture());
        assertEquals("TKL", savedCaptor.getValue().getCode());
        assertEquals(12, savedCaptor.getValue().getDormantAfterMonths());
        assertEquals("TKL", auditNote.getObjectId());
        assertEquals("Chuyển ngủ đông sau: 18 tháng → 12 tháng", auditNote.getDescription());
        assertEquals(1, auditNote.getChanges().size());
    }

    @Test
    @DisplayName("Xóa loại tài khoản còn tài khoản dùng thì báo 409 kèm số tài khoản, không xóa")
    void deleteAccountTypeInUseThrowsConflict() {
        // Chuẩn bị: còn 612 tài khoản dùng loại TKL
        when(accountTypeMapper.findById(2L)).thenReturn(accountType("TKL", "Tài khoản lương", 12, 612));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> accountTypeService.delete(2L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Không xóa được Tài khoản lương: còn 612 tài khoản dùng loại này.", exception.getMessage());
        verify(accountTypeMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("Xóa loại tài khoản không còn ai dùng thì xóa và audit ghi tên loại")
    void deleteUnusedAccountTypeDeletesAndDescribes() {
        // Chuẩn bị
        when(accountTypeMapper.findById(4L)).thenReturn(accountType("TKSV", "Tài khoản sinh viên", 6, 0));

        // Chạy
        accountTypeService.delete(4L);

        // Kiểm tra kết quả
        verify(accountTypeMapper).deleteById(4L);
        assertEquals("TKSV", auditNote.getObjectId());
        assertEquals("Tài khoản sinh viên", auditNote.getDescription());
    }

    private AccountType accountType(String code, String name, int months, long accountCount) {
        AccountType accountType = new AccountType();
        accountType.setCode(code);
        accountType.setName(name);
        accountType.setDormantAfterMonths(months);
        accountType.setAccountCount(accountCount);
        return accountType;
    }
}