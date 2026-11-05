package io.github.arielne.alp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ghi giờ bắt đầu bước cuối của đăng nhập (CN-01, CN-22): lúc KeyCloak chuyển trình duyệt về
 * /login/oauth2/code/keycloak, trước khi Spring đổi code lấy token. Giờ này để ở request; đăng nhập thành công thì
 * LoginSuccessHandler chép vào session, và RevokedSessionFilter so nó với mốc thu hồi.
 * Vì sao không dùng giờ tạo session: session có từ lúc chuyển sang trang đăng nhập KeyCloak (người dùng có thể
 * ngồi ở đó vài phút), và đổi mã session sau đăng nhập vẫn giữ giờ tạo cũ, nên người đăng nhập xong sau mốc vẫn bị
 * đẩy ra thêm 1 lần. Vì sao ghi trước khi đổi code: token cấp sau giờ này, nên bắt đầu sau mốc thì token đã có role mới.
 * Không ghi thẳng vào session: đăng nhập lỗi thì session cũ (có thể đã bị thu hồi) không được nhận giờ mới.
 * Không gắn @Component, giống RevokedSessionFilter: SecurityConfig tự tạo và đặt vào chuỗi filter.
 */
public class LoginStartTimeFilter extends OncePerRequestFilter {

    /** Tên thuộc tính lưu giờ bắt đầu đăng nhập, dùng cho cả request (Instant) và session (số mili giây). */
    static final String LOGIN_STARTED_AT = "alp.loginStartedAt";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/login/oauth2/code/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(LOGIN_STARTED_AT, Instant.now());
        filterChain.doFilter(request, response);
    }
}