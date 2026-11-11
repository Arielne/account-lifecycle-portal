package io.github.arielne.alp.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.employee.EmployeeService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho API tài khoản (spec khách hàng tài khoản mục 7, test 2 đến 4): phân quyền (QT-07), ghép tham số
 * trên URL vào AccountQuery, tham số sai thì 400. AccountService là mock; nghiệp vụ test ở AccountServiceTest.
 */
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    // SecurityConfig (bộ lọc phiên bị thu hồi) và LoginSuccessHandler cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Chưa đăng nhập xem danh sách tài khoản thì trả 401 dạng JSON")
    void accountsReturn401WhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/accounts"));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Admin xem danh sách hay chi tiết tài khoản đều bị 403 (Admin không xem tài khoản)")
    void adminGets403() throws Exception {
        // Chuẩn bị: Admin đã đăng nhập

        // Chạy
        ResultActions list = mockMvc.perform(get("/api/accounts").with(loginAs("ADMIN")));
        ResultActions detail = mockMvc.perform(get("/api/accounts/1").with(loginAs("ADMIN")));

        // Kiểm tra kết quả
        list.andExpect(status().isForbidden());
        detail.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vòng đời tài khoản: chưa đăng nhập 401, Admin 403, Giao dịch viên và Kiểm soát viên xem được")
    void statusHistoryIsForMakerAndChecker() throws Exception {
        // Chuẩn bị
        when(accountService.findStatusHistory(9L)).thenReturn(List.of());

        // Chạy, kiểm tra kết quả
        mockMvc.perform(get("/api/accounts/9/status-history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/9/status-history").with(loginAs("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts/9/status-history").with(loginAs("MAKER"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/accounts/9/status-history").with(loginAs("CHECKER"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Giao dịch viên lọc tài khoản: tham số trên URL được ghép đúng, nhiều trạng thái cách nhau dấu phẩy")
    void makerQueryParametersAreBound() throws Exception {
        // Chuẩn bị
        when(accountService.findPage(any())).thenReturn(new AccountPage(List.of(), 0));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/accounts")
                .param("accountNumber", "1099").param("statuses", "ACTIVE,DORMANT").param("accountTypeId", "5")
                .param("branchId", "6").param("customerId", "7").param("page", "2")
                .with(loginAs("MAKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        ArgumentCaptor<AccountQuery> queryCaptor = ArgumentCaptor.forClass(AccountQuery.class);
        verify(accountService).findPage(queryCaptor.capture());
        AccountQuery query = queryCaptor.getValue();
        assertEquals("1099", query.accountNumber());
        assertEquals(List.of(AccountStatus.ACTIVE, AccountStatus.DORMANT), query.statuses());
        assertEquals(5L, query.accountTypeId());
        assertEquals(6L, query.branchId());
        assertEquals(7L, query.customerId());
        assertEquals(2, query.page());
    }

    @Test
    @DisplayName("Số tài khoản có chữ thì 400, lỗi nằm ở ô accountNumber")
    void accountNumberWithLettersReturns400() throws Exception {
        // Chuẩn bị: Kiểm soát viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/accounts").param("accountNumber", "10a9")
                .with(loginAs("CHECKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("accountNumber"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Số tài khoản chỉ gồm 1–10 chữ số"));
    }

    @Test
    @DisplayName("Mã trạng thái không có trong 5 trạng thái thì 400")
    void unknownStatusReturns400() throws Exception {
        // Chuẩn bị: Giao dịch viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/accounts").param("statuses", "ACTIVE,FROZEN")
                .with(loginAs("MAKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Kiểm soát viên xem chi tiết tài khoản không có thì 404 kèm câu báo lỗi")
    void missingAccountReturns404() throws Exception {
        // Chuẩn bị
        when(accountService.findById(99L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/accounts/99").with(loginAs("CHECKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản"));
    }

    private RequestPostProcessor loginAs(String role) {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}