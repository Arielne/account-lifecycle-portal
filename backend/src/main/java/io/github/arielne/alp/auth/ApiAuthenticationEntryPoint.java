package io.github.arielne.alp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Chưa đăng nhập (hoặc phiên đã hết hạn) mà gọi /api/**: trả 401 dạng JSON thay vì chuyển sang trang đăng nhập.
 * React tự quyết định: chuyển tới /oauth2/authorization/keycloak, hoặc hiện thông báo phiên hết hạn.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(),
                "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}