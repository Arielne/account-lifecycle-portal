package io.github.arielne.alp.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Test nhanh cho LoginStartTimeFilter: chỉ ghi giờ ở request KeyCloak chuyển về sau khi đăng nhập,
 * và chỉ ghi vào request, không đụng session (đăng nhập lỗi thì session cũ không nhận giờ mới).
 */
class LoginStartTimeFilterTest {

    private final LoginStartTimeFilter filter = new LoginStartTimeFilter();

    @Test
    @DisplayName("Request KeyCloak chuyển về /login/oauth2/code/keycloak: ghi giờ vào request, không tạo session")
    void callbackRequestGetsLoginStartTime() throws Exception {
        // Chuẩn bị
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        Instant before = Instant.now();

        // Chạy
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // Kiểm tra kết quả
        Instant startedAt = (Instant) request.getAttribute(LoginStartTimeFilter.LOGIN_STARTED_AT);
        assertFalse(startedAt.isBefore(before));
        assertTrue(startedAt.compareTo(Instant.now()) <= 0);
        assertNull(request.getSession(false));
    }

    @Test
    @DisplayName("Request khác (ví dụ /api/me) thì không ghi giờ")
    void otherRequestIsUntouched() throws Exception {
        // Chuẩn bị
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");

        // Chạy
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // Kiểm tra kết quả
        assertNull(request.getAttribute(LoginStartTimeFilter.LOGIN_STARTED_AT));
    }
}