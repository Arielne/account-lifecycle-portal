package io.github.arielne.alp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Đã đăng nhập nhưng không đủ quyền gọi /api/** (sai role, hoặc thiếu CSRF token): trả 403 dạng JSON chung
 * (spec admin mục 3) thay cho trang lỗi mặc định, để React hiện được câu báo lỗi. QT-07.
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.FORBIDDEN.value(),
                "Bạn không có quyền thực hiện thao tác này.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}