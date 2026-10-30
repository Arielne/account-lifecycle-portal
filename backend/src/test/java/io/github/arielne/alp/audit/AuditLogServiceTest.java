package io.github.arielne.alp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test nhanh cho phần đổi tham số của màn Audit log (CN-23) thành điều kiện lọc và phân trang.
 * AuditLogMapper là mock: chỉ kiểm tra AuditLogFilter Service truyền xuống.
 */
class AuditLogServiceTest {

    private AuditLogMapper auditLogMapper;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        when(auditLogMapper.findPage(any())).thenReturn(List.of());
        service = new AuditLogService(auditLogMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("Trang 3 bỏ qua 20 dòng; không gửi trang, hoặc trang nhỏ hơn 1, thì là trang 1")
    void pageBecomesOffset() {
        // Chạy, kiểm tra kết quả
        assertEquals(20, offsetOf(3));
        assertEquals(0, offsetOf(null));
        assertEquals(0, offsetOf(0));
        assertEquals(0, offsetOf(-5));
    }

    @Test
    @DisplayName("Trang rất lớn không làm số dòng bỏ qua bị tràn thành số âm")
    void hugePageDoesNotOverflowOffset() {
        // Chạy, kiểm tra kết quả: (2147483647 - 1) x 10 lớn hơn giới hạn của int
        assertEquals(21_474_836_460L, offsetOf(Integer.MAX_VALUE));
    }

    private long offsetOf(Integer page) {
        service.findPage(new AuditLogQuery(null, null, null, null, null, null, page));
        ArgumentCaptor<AuditLogFilter> filterCaptor = ArgumentCaptor.forClass(AuditLogFilter.class);
        verify(auditLogMapper, atLeastOnce()).findPage(filterCaptor.capture());
        AuditLogFilter filter = filterCaptor.getValue();
        assertEquals(10, filter.getSize());
        return filter.getOffset();
    }
}