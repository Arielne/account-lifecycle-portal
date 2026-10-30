package io.github.arielne.alp.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho API Chi nhánh (spec admin mục 8, test 1–3): phân quyền, lỗi dữ liệu 400, lỗi nghiệp vụ 409.
 * BranchService được thay bằng mock; logic nghiệp vụ test riêng ở BranchServiceTest.
 */
@WebMvcTest(BranchController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class BranchControllerTest {

    private static final String VALID_BODY = """
            {"code": "CN-Q7", "name": "Chi nhánh Quận 7", "address": "12 Nguyễn Thị Thập, TP. Hồ Chí Minh",
             "phone": "02838720011"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BranchService branchService;

    // LoginSuccessHandler (trong SecurityConfig) cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/branches", "PUT, /api/branches/1", "DELETE, /api/branches/1"})
    @DisplayName("Chưa đăng nhập gọi API ghi thì trả 401 dạng JSON")
    void writeApiReturns401WhenNotLoggedIn(String method, String path) throws Exception {
        // Chuẩn bị: không đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).with(csrf()));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/branches", "PUT, /api/branches/1", "DELETE, /api/branches/1"})
    @DisplayName("Kiểm soát viên gọi API ghi thì trả 403 dạng JSON, không chạy tới Service")
    void writeApiReturns403ForChecker(String method, String path) throws Exception {
        // Chuẩn bị: kiểm soát viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(branchService, never()).delete(any());
    }

    @Test
    @DisplayName("Thêm chi nhánh sai điện thoại và thiếu địa chỉ thì trả 400, fieldErrors chỉ đúng 2 ô")
    void createWithInvalidPhoneAndMissingAddressReturns400() throws Exception {
        // Chuẩn bị
        String body = """
                {"code": "CN-Q7", "name": "Chi nhánh Quận 7", "address": "", "phone": "0283"}
                """;

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/branches")
                .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả: thứ tự lỗi không cố định nên chỉ kiểm từng ô có mặt
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')].message")
                        .value("Số điện thoại phải có 10–11 chữ số"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'address')].message")
                        .value("Vui lòng nhập địa chỉ"));
        verify(branchService, never()).create(any());
    }

    @Test
    @DisplayName("Kiểm tra mã chi nhánh còn trống: trả available theo Service")
    void codeAvailableReturnsServiceResult() throws Exception {
        // Chuẩn bị: mã CN-Q1 đã có
        when(branchService.isCodeAvailable("CN-Q1")).thenReturn(false);

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/branches/code-available").param("code", "CN-Q1")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("Xóa chi nhánh đang được dùng thì trả 409 kèm câu nói rõ còn gì")
    void deleteBranchInUseReturns409() throws Exception {
        // Chuẩn bị: Service báo chi nhánh còn nhân viên và tài khoản
        doThrow(new BusinessException(HttpStatus.CONFLICT,
                "Không xóa được Chi nhánh Quận 1: còn 14 nhân viên, 812 tài khoản thuộc chi nhánh này."))
                .when(branchService).delete(eq(2L));

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.DELETE, "/api/branches/2").with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Không xóa được Chi nhánh Quận 1: còn 14 nhân viên, 812 tài khoản thuộc chi nhánh này."));
    }
}