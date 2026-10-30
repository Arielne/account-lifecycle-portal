package io.github.arielne.alp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.auth.ApiAccessDeniedHandler;
import io.github.arielne.alp.auth.ApiAuthenticationEntryPoint;
import io.github.arielne.alp.auth.KeycloakLogoutSuccessHandler;
import io.github.arielne.alp.auth.KeycloakRoleMapper;
import io.github.arielne.alp.auth.LoginSuccessHandler;
import io.github.arielne.alp.auth.SecurityConfig;
import io.github.arielne.alp.employee.EmployeeService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho GET /api/audit-logs (spec admin mục 8, test 1): chỉ ADMIN đọc được; tham số trên URL
 * được ghép đúng vào AuditLogQuery. AuditLogService là mock; câu lọc test riêng ở AuditLogMapperIT.
 */
@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    // SecurityConfig (bộ lọc nhân viên bị khóa) và LoginSuccessHandler cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Chưa đăng nhập xem audit log thì trả 401 dạng JSON")
    void auditLogReturns401WhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/audit-logs"));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Kiểm soát viên xem audit log thì trả 403 dạng JSON")
    void auditLogReturns403ForChecker() throws Exception {
        // Chuẩn bị: kiểm soát viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/audit-logs")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Admin xem audit log: tham số lọc trên URL được ghép đúng vào AuditLogQuery")
    void adminQueryParametersAreBound() throws Exception {
        // Chuẩn bị
        when(auditLogService.findPage(any())).thenReturn(new AuditLogPage(List.of(), 0));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/audit-logs")
                .param("system", "true").param("from", "2026-10-01").param("to", "2026-10-31")
                .param("action", "UPDATE").param("objectType", "BRANCH").param("page", "2")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả: ArgumentCaptor bắt object Controller truyền cho Service
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogService).findPage(queryCaptor.capture());
        AuditLogQuery query = queryCaptor.getValue();
        assertTrue(query.system());
        assertEquals(LocalDate.of(2026, 10, 1), query.from());
        assertEquals(LocalDate.of(2026, 10, 31), query.to());
        assertEquals("UPDATE", query.action());
        assertEquals("BRANCH", query.objectType());
        assertEquals(2, query.page());
    }
}