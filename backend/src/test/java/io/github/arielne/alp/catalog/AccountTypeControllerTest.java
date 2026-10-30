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
import java.util.List;
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
 * Test nhanh cho API Loại tài khoản (spec admin mục 8, test 1–3): phân quyền, lỗi dữ liệu 400, lỗi nghiệp vụ 409.
 * AccountTypeService được thay bằng mock; logic nghiệp vụ test riêng ở AccountTypeServiceTest.
 */
@WebMvcTest(AccountTypeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class AccountTypeControllerTest {

    private static final String VALID_BODY = """
            {"code": "TKSV", "name": "Tài khoản sinh viên", "description": "Miễn phí quản lý",
             "dormantAfterMonths": 6}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountTypeService accountTypeService;

    // LoginSuccessHandler (trong SecurityConfig) cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    // @ParameterizedTest chạy cùng một test với từng dòng của @CsvSource: mỗi dòng là method và đường dẫn của một API ghi.
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/account-types", "PUT, /api/account-types/1", "DELETE, /api/account-types/1"})
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
    @CsvSource({"POST, /api/account-types", "PUT, /api/account-types/1", "DELETE, /api/account-types/1"})
    @DisplayName("Giao dịch viên gọi API ghi thì trả 403 dạng JSON, không chạy tới Service")
    void writeApiReturns403ForMaker(String method, String path) throws Exception {
        // Chuẩn bị: giao dịch viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MAKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này."));
        verify(accountTypeService, never()).delete(any());
    }

    @Test
    @DisplayName("Giao dịch viên đọc được danh sách loại tài khoản (dùng để chọn ở màn khác)")
    void makerCanReadAccountTypes() throws Exception {
        // Chuẩn bị
        AccountType salaryAccount = new AccountType();
        salaryAccount.setCode("TKL");
        when(accountTypeService.findAll()).thenReturn(List.of(salaryAccount));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/account-types")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MAKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TKL"));
    }

    @Test
    @DisplayName("Thêm loại tài khoản sai mã thì trả 400, fieldErrors chỉ đúng ô code")
    void createWithInvalidCodeReturns400WithFieldError() throws Exception {
        // Chuẩn bị: mã có chữ thường và dài quá 10 ký tự
        String body = """
                {"code": "tk-sinh-vien", "name": "Tài khoản sinh viên", "dormantAfterMonths": 6}
                """;

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/account-types")
                .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu chưa hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mã gồm 2–10 ký tự in hoa hoặc số"));
        verify(accountTypeService, never()).create(any());
    }

    @Test
    @DisplayName("Thêm loại tài khoản trùng mã thì trả 409 kèm câu báo lỗi của Service")
    void createWithDuplicateCodeReturns409() throws Exception {
        // Chuẩn bị: Service báo mã đã có
        when(accountTypeService.create(any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "Mã loại TKSV đã tồn tại"));

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/account-types")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Mã loại TKSV đã tồn tại"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(0));
    }

    @Test
    @DisplayName("Xóa loại tài khoản đang được dùng thì trả 409")
    void deleteAccountTypeInUseReturns409() throws Exception {
        // Chuẩn bị: Service báo còn tài khoản dùng loại này
        doThrow(new BusinessException(HttpStatus.CONFLICT,
                "Không xóa được Tài khoản lương: còn 612 tài khoản dùng loại này."))
                .when(accountTypeService).delete(eq(2L));

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.DELETE, "/api/account-types/2").with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không xóa được Tài khoản lương: còn 612 tài khoản dùng loại này."));
    }
}