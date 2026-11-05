package io.github.arielne.alp.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Test nhanh cho API khách hàng (spec khách hàng tài khoản mục 7, test 2 đến 4): phân quyền (QT-07), ghép thân
 * request vào CustomerSearchRequest, lỗi 400 và 404. CustomerService là mock; nghiệp vụ test ở CustomerServiceTest.
 */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class CustomerControllerTest {

    private static final String SEARCH_BODY = """
            {"searchBy": "NAME", "keyword": "binh", "page": 2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    // SecurityConfig (bộ lọc phiên bị thu hồi) và LoginSuccessHandler cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Chưa đăng nhập tìm khách thì trả 401 dạng JSON")
    void searchReturns401WhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập, vẫn gửi CSRF token như trình duyệt

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/customers/search").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Admin tìm khách hay xem chi tiết khách đều bị 403 (Admin không xem khách hàng)")
    void adminGets403() throws Exception {
        // Chuẩn bị: Admin đã đăng nhập

        // Chạy
        ResultActions search = mockMvc.perform(post("/api/customers/search").with(loginAs("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY));
        ResultActions detail = mockMvc.perform(get("/api/customers/1").with(loginAs("ADMIN")));

        // Kiểm tra kết quả
        search.andExpect(status().isForbidden());
        detail.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Giao dịch viên tìm khách: thân request được ghép đúng vào CustomerSearchRequest")
    void makerSearchBodyIsBound() throws Exception {
        // Chuẩn bị
        when(customerService.search(any())).thenReturn(new CustomerPage(List.of(), 0));

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/customers/search").with(loginAs("MAKER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        ArgumentCaptor<CustomerSearchRequest> requestCaptor = ArgumentCaptor.forClass(CustomerSearchRequest.class);
        verify(customerService).search(requestCaptor.capture());
        CustomerSearchRequest request = requestCaptor.getValue();
        assertEquals(CustomerSearchBy.NAME, request.searchBy());
        assertEquals("binh", request.keyword());
        assertEquals(2, request.page());
    }

    @Test
    @DisplayName("Không chọn tìm theo gì thì 400, lỗi nằm ở ô searchBy")
    void missingSearchByReturns400() throws Exception {
        // Chuẩn bị: Kiểm soát viên đã đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/customers/search").with(loginAs("CHECKER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\": \"binh\"}"));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("searchBy"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Chọn tìm theo họ tên hoặc số CCCD"));
    }

    @Test
    @DisplayName("Service báo CCCD chưa đủ 12 số thì API trả 400 kèm câu báo lỗi")
    void shortCccdReturns400WithMessage() throws Exception {
        // Chuẩn bị
        when(customerService.search(any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "Số CCCD phải đủ 12 chữ số"));

        // Chạy
        ResultActions result = mockMvc.perform(post("/api/customers/search").with(loginAs("MAKER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"searchBy\": \"CCCD\", \"keyword\": \"0990\"}"));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số CCCD phải đủ 12 chữ số"));
    }

    @Test
    @DisplayName("Kiểm soát viên xem chi tiết khách không có thì 404 kèm câu báo lỗi")
    void missingCustomerReturns404() throws Exception {
        // Chuẩn bị
        when(customerService.findById(99L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/customers/99").with(loginAs("CHECKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy khách hàng"));
    }

    private RequestPostProcessor loginAs(String role) {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}