package io.github.arielne.alp.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.employee.EmployeeService;
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
 * Test nhanh cho bộ lọc nhân viên bị khóa (spec admin mục 8, test 7): người bị khóa gọi /api thì nhận 401
 * và session bị xóa, ngay cả khi session vẫn còn hạn. Dùng GET /api/me làm API bất kỳ.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class LockedEmployeeFilterTest {

    private static final String KHOA_KEYCLOAK_ID = "f862d39b-b598-4d60-9a20-ae0c70ac22bc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Nhân viên đã bị khóa gọi /api thì nhận 401 dạng JSON và session bị xóa")
    void lockedEmployeeGets401AndSessionInvalidated() throws Exception {
        // Chuẩn bị: Khoa đang đăng nhập nhưng Admin vừa khóa
        when(employeeService.isLocked(KHOA_KEYCLOAK_ID)).thenReturn(true);
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
    @DisplayName("Nhân viên chưa bị khóa thì đi qua bộ lọc bình thường")
    void activeEmployeePassesThrough() throws Exception {
        // Chuẩn bị: không khóa (mock trả false)
        MockHttpSession session = new MockHttpSession();

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me").session(session)
                .with(oidcLogin().idToken(token -> token.subject(KHOA_KEYCLOAK_ID))
                        .authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isOk());
        assertFalse(session.isInvalid());
    }
}