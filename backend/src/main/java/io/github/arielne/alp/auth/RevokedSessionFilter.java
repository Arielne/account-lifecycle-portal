package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.EmployeeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đẩy ra ngay ở request kế tiếp người có phiên bị thu hồi (CN-22, spec admin mục 6): nhân viên bị khóa,
 * hoặc nhân viên vừa bị Admin bỏ role. Session trong Redis chỉ hết hạn sau 30 phút không hoạt động, và role
 * trong session là role lúc đăng nhập, nên thiếu bộ lọc này thì người bị bỏ role vẫn dùng role đó tới khi tự đăng xuất.
 * Mỗi request vào /api của người đã đăng nhập hỏi EmployeeService; phiên bị thu hồi thì xóa session và trả 401
 * dạng JSON. React hiện hộp thoại phiên hết hạn; đăng nhập lại thì KeyCloak từ chối (bị khóa) hoặc cấp token
 * có role mới (bị bỏ role).
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
                && employeeService.isSessionRevoked(user.getSubject(), sessionCreatedAt(session))) {
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            apiAuthenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Phiên đăng nhập đã bị thu hồi"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Lúc session được tạo, tức lúc bắt đầu đăng nhập (Spring Session giữ nguyên lúc tạo khi đổi mã session sau đăng nhập).
     * Không có session (đăng nhập OAuth2 luôn có) thì coi như phiên vừa tạo.
     */
    private Instant sessionCreatedAt(HttpSession session) {
        if (session == null) {
            return Instant.now();
        }
        return Instant.ofEpochMilli(session.getCreationTime());
    }
}