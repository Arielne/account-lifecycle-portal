package io.github.arielne.alp.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.customer.CustomerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * Test 4 của spec phiếu (QT-02): đổi trạng thái tài khoản qua State pattern. Chạy đủ 25 cặp (5 trạng thái × 5 trạng
 * thái): đúng các cặp ở docs/01-chuc-nang.md mục 2.1 thì đổi được, còn lại 409 và không ghi gì.
 * Danh sách cặp hợp lệ chép tay từ file chức năng, không đọc từ các lớp trạng thái, để test thật sự kiểm các lớp đó.
 */
class AccountStatusChangeTest {

    /** Các lần chuyển hợp lệ ở mục 2.1 (trừ "chưa có → ...", việc của hàm mở tài khoản). */
    private static final Set<String> ALLOWED_PAIRS = Set.of(
            "PENDING_OPEN>ACTIVE", "PENDING_OPEN>CLOSED",
            "ACTIVE>BLOCKED", "ACTIVE>DORMANT", "ACTIVE>CLOSED",
            "BLOCKED>ACTIVE", "BLOCKED>CLOSED",
            "DORMANT>ACTIVE", "DORMANT>CLOSED");

    private AccountMapper accountMapper;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        service = new AccountService(accountMapper, mock(CustomerService.class));
    }

    static List<Arguments> allPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (AccountStatus from : AccountStatus.values()) {
            for (AccountStatus to : AccountStatus.values()) {
                pairs.add(Arguments.of(from, to));
            }
        }
        return pairs;
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allPairs")
    @DisplayName("QT-02: 25 cặp trạng thái, chỉ cặp có trong mục 2.1 được đổi, còn lại 409")
    void onlyListedTransitionsAreAllowed(AccountStatus from, AccountStatus to) {
        // Chuẩn bị: tài khoản 9 đang ở trạng thái from, đúng như lúc tạo phiếu
        when(accountMapper.lockStatus(9L)).thenReturn(from.name());
        boolean allowed = ALLOWED_PAIRS.contains(from.name() + ">" + to.name());

        // Chạy và kiểm tra kết quả
        if (allowed) {
            service.changeStatus(9L, from, to, 1L, 7L);
            verify(accountMapper).updateStatus(eq(9L), eq(to.name()), anyBoolean(), anyBoolean(), any());
            verify(accountMapper).insertStatusHistory(9L, from.name(), to.name(), 1L, 7L);
        } else {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.changeStatus(9L, from, to, 1L, 7L));
            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertEquals("Không chuyển được tài khoản từ " + from.getLabel() + " sang " + to.getLabel(),
                    exception.getMessage());
            verify(accountMapper, never()).updateStatus(anyLong(), anyString(), anyBoolean(), anyBoolean(), any());
            verify(accountMapper, never()).insertStatusHistory(any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("D7: trạng thái tài khoản đã khác lúc tạo phiếu (job vừa chuyển Ngủ đông) thì 409, không đổi gì")
    void statusChangedAfterTicketCreatedThrowsConflict() {
        // Chuẩn bị: phiếu Phong tỏa tạo lúc tài khoản Hoạt động, nhưng giờ tài khoản đã Ngủ đông
        when(accountMapper.lockStatus(9L)).thenReturn("DORMANT");

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus(9L, AccountStatus.ACTIVE, AccountStatus.BLOCKED, 1L, 7L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Trạng thái tài khoản đã đổi từ Hoạt động sang Ngủ đông sau khi tạo phiếu. Hãy từ chối phiếu này.",
                exception.getMessage());
        verify(accountMapper, never()).updateStatus(anyLong(), anyString(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Sang Hoạt động thì báo đang kích hoạt; sang Đã đóng thì báo đang đóng (để đặt ngày mở, ngày đóng)")
    void activatingAndClosingFlagsFollowTarget() {
        // Chuẩn bị
        when(accountMapper.lockStatus(9L)).thenReturn("PENDING_OPEN");
        when(accountMapper.lockStatus(10L)).thenReturn("BLOCKED");

        // Chạy
        service.changeStatus(9L, AccountStatus.PENDING_OPEN, AccountStatus.ACTIVE, 1L, 7L);
        service.changeStatus(10L, AccountStatus.BLOCKED, AccountStatus.CLOSED, 2L, 7L);

        // Kiểm tra kết quả
        verify(accountMapper).updateStatus(eq(9L), eq("ACTIVE"), eq(true), eq(false), any());
        verify(accountMapper).updateStatus(eq(10L), eq("CLOSED"), eq(false), eq(true), any());
    }

    @Test
    @DisplayName("Tài khoản không có thì 404")
    void missingAccountThrowsNotFound() {
        // Chuẩn bị
        when(accountMapper.lockStatus(99L)).thenReturn(null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus(99L, AccountStatus.ACTIVE, AccountStatus.BLOCKED, 1L, 7L));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}