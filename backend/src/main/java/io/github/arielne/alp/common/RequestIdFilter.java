package io.github.arielne.alp.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gán cho mỗi request vào /api một mã UUID (spec admin mục 3), để tra lỗi: lỗi 500 ghi log kèm mã này,
 * và AuditAspect lưu mã vào audit_log.request_id.
 * Mã nằm trong MDC (Mapped Diagnostic Context) của thư viện log: một chỗ lưu riêng cho từng luồng xử lý,
 * mọi dòng log của request này tự có mã (xem logging.pattern.level trong application.yml).
 * OncePerRequestFilter: Spring bảo đảm filter chạy đúng 1 lần cho mỗi request.
 * HIGHEST_PRECEDENCE: chạy trước Spring Security, nên cả log của lỗi 401, 403 cũng có mã.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";

    /** Mã của request đang chạy; null khi không nằm trong một request vào /api. */
    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.put(MDC_KEY, UUID.randomUUID().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Luồng xử lý được Tomcat dùng lại cho request khác, nên phải xóa mã khi xong.
            MDC.remove(MDC_KEY);
        }
    }
}