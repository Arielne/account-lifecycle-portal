package io.github.arielne.alp.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.employee.EmployeeService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho bộ lọc phiên bị thu hồi (spec admin mục 8, test 7): người bị khóa hoặc vừa bị bỏ role gọi /api
 * thì nhận 401 và session bị xóa, ngay cả khi session vẫn còn hạn. Dùng GET /api/me làm API bất kỳ.
 * Khi nào phiên bị thu hồi do EmployeeService quyết định (EmployeeServiceTest); ở đây chỉ kiểm tra bộ lọc.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class RevokedSessionFilterTest {

    private static final String KHOA_KEYCLOAK_ID = "f862d39b-b598-4d60-9a20-ae0c70ac22bc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Phiên đã bị thu hồi (bị khóa hoặc bị bỏ role) gọi /api thì nhận 401 dạng JSON và session bị xóa")
    void revokedSessionGets401AndSessionInvalidated() throws Exception {
        // Chuẩn bị: Khoa đang đăng nhập nhưng Admin vừa bỏ role Kiểm soát viên
        when(employeeService.isSessionRevoked(eq(KHOA_KEYCLOAK_ID), any())).thenReturn(true);
        MockHttpSession session = new MockHttpSession();

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me").session(session)
                .with(oidcLogin().idToken(token -> token.subject(KHOA_KEYCLOAK_ID))
                        .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("Phiên chưa bị thu hồi thì đi qua bộ lọc bình thường; bộ lọc gửi đúng lúc session được tạo")
    void validSessionPassesThrough() throws Exception {
        // Chuẩn bị: không thu hồi (mock trả false)
        MockHttpSession session = new MockHttpSession();

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me").session(session)
                .with(oidcLogin().idToken(token -> token.subject(KHOA_KEYCLOAK_ID))
                        .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isOk());
        assertFalse(session.isInvalid());
        verify(employeeService).isSessionRevoked(KHOA_KEYCLOAK_ID, Instant.ofEpochMilli(session.getCreationTime()));
    }
}