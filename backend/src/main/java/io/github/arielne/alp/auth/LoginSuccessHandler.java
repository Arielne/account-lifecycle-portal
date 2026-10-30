package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Chạy ngay sau khi đăng nhập qua KeyCloak thành công (CN-01, CN-03):
 * thêm hoặc cập nhật hồ sơ nhân viên theo ID token, rồi chuyển trình duyệt về trang chủ "/".
 * Người tự đăng ký đăng nhập lần đầu cũng có hồ sơ từ đây, chưa có chi nhánh, và có dòng audit "Tự đăng ký".
 * Email trong token đã thuộc hồ sơ khác (spec admin mục 6): không lưu hồ sơ, xóa session, đăng xuất khỏi
 * KeyCloak rồi về trang /loi-dang-nhap của React. Không nối hồ sơ theo email, vì realm không bắt xác minh email.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String LOGIN_ERROR_PATH = "/loi-dang-nhap";

    private final EmployeeService employeeService;
    private final KeycloakLogoutSuccessHandler keycloakLogoutSuccessHandler;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        // sub trong token chính là id của user trên KeyCloak, lưu ở cột keycloak_id.
        String keycloakId = user.getSubject();
        if (employeeService.isEmailUsedByOtherEmployee(user.getEmail(), keycloakId)) {
            rejectDuplicateEmail(request, response, authentication);
            return;
        }
        String fullName = VietnameseFullName.fromToken(user);
        if (employeeService.findByKeycloakId(keycloakId) == null) {
            employeeService.registerFromFirstLogin(keycloakId, user.getEmail(), fullName);
        } else {
            employeeService.saveProfileFromLogin(keycloakId, user.getEmail(), fullName);
        }
        response.sendRedirect("/");
    }

    /** Xóa session của app, rồi đi qua trang đăng xuất của KeyCloak để kết thúc luôn phiên SSO trước khi về trang lỗi. */
    private void rejectDuplicateEmail(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException {
        // Lấy địa chỉ đăng xuất trước khi xóa session: cần ID token (id_token_hint) của người vừa đăng nhập.
        String logoutUrl = keycloakLogoutSuccessHandler.buildLogoutUrl(request, authentication, LOGIN_ERROR_PATH);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.sendRedirect(logoutUrl);
    }
}