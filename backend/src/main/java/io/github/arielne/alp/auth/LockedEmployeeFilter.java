package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.EmployeeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đẩy nhân viên bị khóa ra ngay ở request kế tiếp (CN-22, spec admin mục 6).
 * Người bị khóa có thể đang đăng nhập, mà session trong Redis chỉ hết hạn sau 30 phút không hoạt động.
 * Vì vậy mỗi request vào /api của người đã đăng nhập đọc employee.status; gặp LOCKED thì xóa session và trả 401
 * dạng JSON. React hiện hộp thoại phiên hết hạn; đăng nhập lại thì KeyCloak từ chối vì tài khoản đã tắt.
 * Không gắn @Component: SecurityConfig tự tạo và đặt filter này vào chuỗi filter của Spring Security,
 * nếu là bean thì Spring Boot đăng ký thêm một lần nữa ở ngoài chuỗi đó.
 */
public class LockedEmployeeFilter extends OncePerRequestFilter {

    private final EmployeeService employeeService;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    public LockedEmployeeFilter(EmployeeService employeeService,
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
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user
                && employeeService.isLocked(user.getSubject())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            apiAuthenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Nhân viên đã bị khóa"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}