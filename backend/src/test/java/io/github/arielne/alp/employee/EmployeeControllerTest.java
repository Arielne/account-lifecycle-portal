package io.github.arielne.alp.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * Test nhanh cho API Nhân viên (spec admin mục 8, test 1, 5): phân quyền, lỗi dữ liệu 400, lỗi nghiệp vụ 409.
 * EmployeeManagementService là mock; nghiệp vụ test riêng ở EmployeeManagementServiceTest.
 */
@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class EmployeeControllerTest {

    private static final String ADMIN_KEYCLOAK_ID = "927c941a-3244-4299-8a41-7c78b44d08e6";
    private static final String VALID_BODY = """
            {"fullName": "Nguyễn Văn An", "email": "an.nv@nhdemo.local", "branchId": 4, "roles": ["MAKER"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeManagementService employeeManagementService;

    // SecurityConfig (bộ lọc nhân viên bị khóa) và LoginSuccessHandler cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/employees", "PUT, /api/employees/7", "PUT, /api/employees/7/roles",
            "POST, /api/employees/7/lock", "POST, /api/employees/7/unlock"})
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
    @CsvSource({"GET, /api/employees", "POST, /api/employees", "PUT, /api/employees/7",
            "PUT, /api/employees/7/roles", "POST, /api/employees/7/lock", "POST, /api/employees/7/unlock"})
    @DisplayName("Kiểm soát viên gọi API Nhân viên (kể cả đọc) thì trả 403 dạng JSON")
    void employeeApiReturns403ForChecker(String method, String path) throws Exception {
        // Chuẩn bị: kiểm soát viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_CHECKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Thêm nhân viên họ tên một từ, email sai dạng, không chọn role thì trả 400 với lỗi 3 ô")
    void createWithInvalidDataReturns400() throws Exception {
        // Chuẩn bị
        String body = """
                {"fullName": "An", "email": "an.nv", "roles": []}
                """;

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf())
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')].message").value("Họ tên phải có ít nhất 2 từ"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].message").value("Email chưa đúng dạng"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'roles')].message").value("Chọn ít nhất một role"));
        verify(employeeManagementService, never()).create(any());
    }

    @Test
    @DisplayName("Admin khóa chính mình thì trả 409; Controller đưa đúng keycloak_id của người đang đăng nhập cho Service")
    void lockSelfReturns409() throws Exception {
        // Chuẩn bị: Service từ chối vì là chính người đang đăng nhập
        doThrow(new BusinessException(HttpStatus.CONFLICT, "Không thể tự khóa chính mình"))
                .when(employeeManagementService).lock(eq(4L), eq(ADMIN_KEYCLOAK_ID));

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/employees/4/lock").with(csrf())
                .with(oidcLogin().idToken(token -> token.subject(ADMIN_KEYCLOAK_ID))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể tự khóa chính mình"));
    }

    @Test
    @DisplayName("Admin đọc được danh sách nhân viên")
    void adminCanReadEmployees() throws Exception {
        // Chuẩn bị: danh sách rỗng là đủ để kiểm quyền

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/employees")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // Kiểm tra kết quả
        result.andExpect(status().isOk());
    }
}