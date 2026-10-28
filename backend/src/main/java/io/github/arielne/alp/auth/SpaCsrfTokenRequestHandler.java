package io.github.arielne.alp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF cho SPA, theo mẫu "Single-Page Applications" trong tài liệu Spring Security 6.5.
 * Token nằm trong cookie XSRF-TOKEN (JavaScript đọc được); axios tự gửi lại qua header X-XSRF-TOKEN.
 * Trang lạ không đọc được cookie của portal, nên không gửi được header đúng.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plainHandler = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Luôn dùng bản XOR khi đưa token vào trang, để chống tấn công BREACH.
        this.xorHandler.handle(request, response, csrfToken);
        // Gọi get() để token được tạo ngay và ghi vào cookie XSRF-TOKEN ở mọi response.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        // Có header X-XSRF-TOKEN (axios gửi giá trị gốc lấy từ cookie): so sánh trực tiếp.
        // Không có header (form gửi tham số _csrf): giải mã theo bản XOR.
        CsrfTokenRequestHandler handler = StringUtils.hasText(headerValue) ? this.plainHandler : this.xorHandler;
        return handler.resolveCsrfTokenValue(request, csrfToken);
    }
}