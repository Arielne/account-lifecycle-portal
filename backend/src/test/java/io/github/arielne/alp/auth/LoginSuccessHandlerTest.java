package io.github.arielne.alp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.employee.Employee;
import io.github.arielne.alp.employee.EmployeeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Test nhanh cho bước ngay sau khi đăng nhập (spec admin mục 8, test 8).
 * Lỗi phát hiện khi thử đăng nhập: email trong token đã thuộc hồ sơ có keycloak_id khác thì câu INSERT
 * vi phạm UNIQUE của cột email và người dùng thấy trang lỗi. EmployeeService là mock; lần lưu hồ sơ được
 * giả lập ném DuplicateKeyException như database thật.
 */
class LoginSuccessHandlerTest {

    private static final String NEW_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000d1";
    private static final String EMAIL = "thu.dangky@nhdemo.local";

    private EmployeeService employeeService;
    private LoginSuccessHandler loginSuccessHandler;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        KeycloakLogoutSuccessHandler logoutHandler =
                new KeycloakLogoutSuccessHandler("http://localhost:8080", new ObjectMapper());
        loginSuccessHandler = new LoginSuccessHandler(employeeService, logoutHandler);
    }

    @Test
    @DisplayName("Email đã thuộc hồ sơ khác: không lưu hồ sơ, xóa session, đăng xuất KeyCloak rồi về /loi-dang-nhap")
    void emailOfAnotherProfileRedirectsToLoginErrorPage() throws Exception {
        // Chuẩn bị: hồ sơ khác đã dùng email này; lưu hồ sơ sẽ vi phạm UNIQUE như database thật
        when(employeeService.isEmailUsedByOtherEmployee(EMAIL, NEW_KEYCLOAK_ID)).thenReturn(true);
        doThrow(new DuplicateKeyException("employee_email_key"))
                .when(employeeService).saveProfileFromLogin(any(), any(), any());
        doThrow(new DuplicateKeyException("employee_email_key"))
                .when(employeeService).registerFromFirstLogin(any(), any(), any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Chạy
        loginSuccessHandler.onAuthenticationSuccess(request, response, loggedIn(NEW_KEYCLOAK_ID, EMAIL));

        // Kiểm tra kết quả: đi qua trang đăng xuất của KeyCloak để kết thúc phiên SSO, rồi KeyCloak trả về /loi-dang-nhap
        assertTrue(session.isInvalid());
        String redirectUrl = response.getRedirectedUrl();
        assertTrue(redirectUrl.startsWith("http://localhost:8080/realms/alp/protocol/openid-connect/logout?"));
        assertTrue(redirectUrl.contains("post_logout_redirect_uri=http://localhost/loi-dang-nhap"));
        assertTrue(redirectUrl.contains("id_token_hint=id-token-value"));
        verify(employeeService, never()).saveProfileFromLogin(any(), any(), any());
        verify(employeeService, never()).registerFromFirstLogin(any(), any(), any());
    }

    @Test
    @DisplayName("Người tự đăng ký đăng nhập lần đầu thì tạo hồ sơ qua registerFromFirstLogin (ghi audit Tự đăng ký)")
    void firstLoginRegistersProfile() throws Exception {
        // Chuẩn bị: chưa có hồ sơ
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Chạy
        loginSuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                loggedIn(NEW_KEYCLOAK_ID, EMAIL));

        // Kiểm tra kết quả
        verify(employeeService).registerFromFirstLogin(NEW_KEYCLOAK_ID, EMAIL, "Bắc Đẩu Thu");
        verify(employeeService, never()).saveProfileFromLogin(any(), any(), any());
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    @DisplayName("Người đã có hồ sơ đăng nhập lại thì chỉ cập nhật hồ sơ, không ghi audit Tự đăng ký")
    void laterLoginUpdatesProfile() throws Exception {
        // Chuẩn bị: hồ sơ đã có
        when(employeeService.findByKeycloakId(NEW_KEYCLOAK_ID)).thenReturn(new Employee());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Chạy
        loginSuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                loggedIn(NEW_KEYCLOAK_ID, EMAIL));

        // Kiểm tra kết quả
        verify(employeeService).saveProfileFromLogin(NEW_KEYCLOAK_ID, EMAIL, "Bắc Đẩu Thu");
        verify(employeeService, never()).registerFromFirstLogin(any(), any(), any());
        assertEquals("/", response.getRedirectedUrl());
    }

    /** Người vừa đăng nhập qua KeyCloak, giống object Spring Security tạo ra sau khi đổi mã lấy token. */
    private Authentication loggedIn(String keycloakId, String email) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-value")
                .subject(keycloakId)
                .claim("email", email)
                .claim("family_name", "Bắc Đẩu")
                .claim("given_name", "Thu")
                .build();
        List<GrantedAuthority> noRoles = List.of();
        return new OAuth2AuthenticationToken(new DefaultOidcUser(noRoles, idToken), noRoles, "keycloak");
    }
}