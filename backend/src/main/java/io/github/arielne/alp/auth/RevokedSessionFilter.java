package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.EmployeeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đẩy ra ngay ở request kế tiếp người có phiên bị thu hồi (CN-22, spec admin mục 6): nhân viên bị khóa,
 * hoặc nhân viên vừa bị Admin bỏ role. Session trong Redis chỉ hết hạn sau 30 phút không hoạt động, và role
 * trong session là role lúc đăng nhập, nên thiếu bộ lọc này thì người bị bỏ role vẫn dùng role đó tới khi tự đăng xuất.
 * Mỗi request vào /api của người đã đăng nhập hỏi EmployeeService; phiên bị thu hồi thì xóa session và trả 401
 * dạng JSON, giống hệt phiên hết hạn vì spec không có thông báo riêng cho việc bị thu hồi. React cũng xử lý như
 * phiên hết hạn: mở trang hoặc F5 (/api/me) thì chuyển sang đăng nhập, API khác thì hiện hộp thoại. Đăng nhập lại
 * thì KeyCloak từ chối (bị khóa) hoặc cấp token có role mới (bị bỏ role).
 * Không gắn @Component: SecurityConfig tự tạo và đặt filter này vào chuỗi filter của Spring Security,
 * nếu là bean thì Spring Boot đăng ký thêm một lần nữa ở ngoài chuỗi đó.
 */
public class RevokedSessionFilter extends OncePerRequestFilter {

    private final EmployeeService employeeService;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    public RevokedSessionFilter(EmployeeService employeeService,
                                ApiAuthenticationEntryPoint apiAuthenticationEntryPoint) {
        this.employeeService = employeeService;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user
                && employeeService.isSessionRevoked(user.getSubject(), loginStartedAt(session))) {
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            apiAuthenticationEntryPoint.writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Giờ bắt đầu đổi code lấy token của lần đăng nhập này (LoginSuccessHandler lưu, xem LoginStartTimeFilter).
     * Session chưa có giờ đó (đăng nhập trước khi có code này) thì dùng giờ tạo session: sớm hơn giờ đăng nhập,
     * nên chỉ có thể đẩy ra thừa, không để lọt phiên đã bị thu hồi.
     * Không có session (đăng nhập OAuth2 luôn có) thì coi như phiên vừa tạo.
     */
    private Instant loginStartedAt(HttpSession session) {
        if (session == null) {
            return Instant.now();
        }
        if (session.getAttribute(LoginStartTimeFilter.LOGIN_STARTED_AT) instanceof Long startedAtMillis) {
            return Instant.ofEpochMilli(startedAtMillis);
        }
        return Instant.ofEpochMilli(session.getCreationTime());
    }
}