package io.github.arielne.alp.auth;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.employee.Employee;
import io.github.arielne.alp.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test nhanh cho GET /api/me và phân quyền chung của /api (spec mục 8, test 1–4).
 * @WebMvcTest chỉ dựng phần web và Spring Security, không cần database, Redis hay KeyCloak.
 * oidcLogin() giả lập người đã đăng nhập qua KeyCloak; EmployeeService được thay bằng mock.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class})
class MeControllerTest {

    private static final String LAN_KEYCLOAK_ID = "5b30439a-4e15-42ac-bf7d-c4e8dba67784";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Chưa đăng nhập gọi /api/me thì trả 401 dạng JSON")
    void meReturns401JsonWhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me"));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Giao dịch viên gọi /api/me thì nhận đúng họ tên, role và chi nhánh")
    void meReturnsNameRolesAndBranchOfMaker() throws Exception {
        // Chuẩn bị: hồ sơ của Lan đã gán chi nhánh Tân Phú
        Employee lan = new Employee();
        lan.setBranchId(4L);
        lan.setBranchCode("CN-TP");
        lan.setBranchName("Chi nhánh Tân Phú");
        when(employeeService.findByKeycloakId(LAN_KEYCLOAK_ID)).thenReturn(lan);

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me").with(oidcLogin()
                .idToken(token -> token.subject(LAN_KEYCLOAK_ID)
                        .claim("family_name", "Nguyễn Thị")
                        .claim("given_name", "Lan")
                        .claim("email", "lan.nt@nhdemo.local"))
                .authorities(new SimpleGrantedAuthority("ROLE_MAKER"))));

        // Kiểm tra kết quả: họ tên ghép Họ trước Tên sau
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloakId").value(LAN_KEYCLOAK_ID))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Thị Lan"))
                .andExpect(jsonPath("$.email").value("lan.nt@nhdemo.local"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("MAKER"))
                .andExpect(jsonPath("$.branch.id").value(4))
                .andExpect(jsonPath("$.branch.code").value("CN-TP"))
                .andExpect(jsonPath("$.branch.name").value("Chi nhánh Tân Phú"));
    }

    @Test
    @DisplayName("Người chưa có role gọi /api/me thì vẫn được 200, roles rỗng, branch null")
    void meReturnsEmptyRolesForUserWithoutRole() throws Exception {
        // Chuẩn bị: người tự đăng ký, chưa có role, chưa có chi nhánh

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/me").with(oidcLogin()));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(0))
                .andExpect(jsonPath("$.branch").isEmpty());
    }

    @Test
    @DisplayName("Người chưa có role gọi một API khác trong /api thì bị 403")
    void otherApiReturns403ForUserWithoutRole() throws Exception {
        // Chuẩn bị: người chưa có role

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/khach-hang").with(oidcLogin()));

        // Kiểm tra kết quả
        result.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Người có role gọi một đường dẫn /api chưa có thì nhận 404, không phải 403")
    void unknownApiReturns404ForUserWithRole() throws Exception {
        // Chuẩn bị: giao dịch viên

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/chua-co")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MAKER"))));

        // Kiểm tra kết quả
        result.andExpect(status().isNotFound());
    }
}