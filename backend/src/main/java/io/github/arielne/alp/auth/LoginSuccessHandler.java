package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Chạy ngay sau khi đăng nhập qua KeyCloak thành công (CN-01, CN-03):
 * thêm hoặc cập nhật hồ sơ nhân viên theo ID token, rồi chuyển trình duyệt về trang chủ "/".
 * Người tự đăng ký đăng nhập lần đầu cũng có hồ sơ từ đây, chưa có chi nhánh.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final EmployeeService employeeService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        // sub trong token chính là id của user trên KeyCloak, lưu ở cột keycloak_id.
        employeeService.saveProfileFromLogin(user.getSubject(), user.getEmail(), VietnameseFullName.fromToken(user));
        response.sendRedirect("/");
    }
}