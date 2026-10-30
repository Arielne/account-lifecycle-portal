package io.github.arielne.alp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.AuditChange;
import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho nghiệp vụ chi nhánh (spec admin mục 8, test 2–3): mã trùng khi sửa, không xóa khi đang dùng,
 * mô tả audit khi sửa 2 trường. BranchMapper là mock của Mockito, AuditNote là object thường.
 */
class BranchServiceTest {

    private BranchMapper branchMapper;
    private AuditNote auditNote;
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        branchMapper = mock(BranchMapper.class);
        auditNote = new AuditNote();
        branchService = new BranchService(branchMapper, auditNote);
    }

    @Test
    @DisplayName("Xóa chi nhánh còn nhân viên và tài khoản thì báo 409 nói rõ còn gì, không xóa")
    void deleteBranchInUseThrowsConflict() {
        // Chuẩn bị: Chi nhánh Quận 1 còn 14 nhân viên, 812 tài khoản
        when(branchMapper.findById(2L)).thenReturn(branch("CN-Q1", "Chi nhánh Quận 1", "45 Lê Thánh Tôn", "02838234510"));
        BranchUsage usage = new BranchUsage();
        usage.setEmployeeCount(14);
        usage.setAccountCount(812);
        when(branchMapper.findUsage(2L)).thenReturn(usage);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> branchService.delete(2L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Không xóa được Chi nhánh Quận 1: còn 14 nhân viên, 812 tài khoản thuộc chi nhánh này.",
                exception.getMessage());
        verify(branchMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("Sửa mã chi nhánh thành mã đã có thì báo 409, không lưu")
    void updateToExistingCodeThrowsConflict() {
        // Chuẩn bị: đổi CN-Q3 thành CN-Q1 đã có
        when(branchMapper.findById(3L)).thenReturn(branch("CN-Q3", "Chi nhánh Quận 3", "210 Võ Văn Tần", "02839307712"));
        when(branchMapper.existsByCode("CN-Q1")).thenReturn(true);
        BranchRequest request = new BranchRequest("CN-Q1", "Chi nhánh Quận 3", "210 Võ Văn Tần", "02839307712");

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> branchService.update(3L, request));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Mã chi nhánh CN-Q1 đã tồn tại", exception.getMessage());
        verify(branchMapper, never()).update(any());
    }

    @Test
    @DisplayName("Sửa địa chỉ và điện thoại thì audit ghi 'Sửa 2 trường', điện thoại hiện theo nhóm số")
    void updateTwoFieldsDescribesBothChanges() {
        // Chuẩn bị: giữ nguyên mã CN-Q3 nên không kiểm tra trùng
        when(branchMapper.findById(3L)).thenReturn(branch("CN-Q3", "Chi nhánh Quận 3", "88 Trần Quốc Thảo", "02839324051"));
        BranchRequest request = new BranchRequest("CN-Q3", "Chi nhánh Quận 3", "210 Võ Văn Tần", "02839307712");

        // Chạy
        branchService.update(3L, request);

        // Kiểm tra kết quả
        verify(branchMapper, never()).existsByCode(any());
        verify(branchMapper).update(any());
        assertEquals("Sửa 2 trường: Địa chỉ, Số điện thoại", auditNote.getDescription());
        AuditChange phoneChange = auditNote.getChanges().get(1);
        assertEquals("028 3932 4051", phoneChange.before());
        assertEquals("028 3930 7712", phoneChange.after());
    }

    private Branch branch(String code, String name, String address, String phone) {
        Branch branch = new Branch();
        branch.setCode(code);
        branch.setName(name);
        branch.setAddress(address);
        branch.setPhone(phone);
        return branch;
    }
}