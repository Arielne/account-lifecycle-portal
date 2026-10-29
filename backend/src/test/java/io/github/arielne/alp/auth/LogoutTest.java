package io.github.arielne.alp.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho POST /logout (spec mục 8, test 5): phải có CSRF token, trả địa chỉ đăng xuất của KeyCloak dạng JSON.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class})
class LogoutTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("POST /logout thiếu CSRF token thì bị 403")
    void logoutWithoutCsrfReturns403() throws Exception {
        // Chuẩn bị: đã đăng nhập nhưng request không kèm CSRF token

        // Chạy
        ResultActions result = mockMvc.perform(post("/logout")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MAKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /logout đủ CSRF token thì trả 200 và logoutUrl của KeyCloak kèm id_token_hint, post_logout_redirect_uri")
    void logoutWithCsrfReturnsKeycloakLogoutUrl() throws Exception {
        // Chuẩn bị: đã đăng nhập, request kèm CSRF token

        // Chạy
        ResultActions result = mockMvc.perform(post("/logout")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MAKER")))
                .with(csrf()));

        // Kiểm tra kết quả: MockMvc gọi bằng http://localhost nên trang chủ quay về là http://localhost/
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl",
                        containsString("http://localhost:8080/realms/alp/protocol/openid-connect/logout")))
                .andExpect(jsonPath("$.logoutUrl", containsString("id_token_hint=")))
                .andExpect(jsonPath("$.logoutUrl",
                        containsString("post_logout_redirect_uri=http://localhost/")));
    }
}