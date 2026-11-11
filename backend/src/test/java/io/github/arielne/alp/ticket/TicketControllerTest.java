package io.github.arielne.alp.ticket;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.auth.ApiAccessDeniedHandler;
import io.github.arielne.alp.auth.ApiAuthenticationEntryPoint;
import io.github.arielne.alp.auth.KeycloakLogoutSuccessHandler;
import io.github.arielne.alp.auth.KeycloakRoleMapper;
import io.github.arielne.alp.auth.LoginSuccessHandler;
import io.github.arielne.alp.auth.SecurityConfig;
import io.github.arielne.alp.employee.EmployeeService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Test nhanh cho API phiếu (spec phiếu mục 13, test 1 và 2): phân quyền theo role (QT-07), Bean Validation của
 * phần "data" trả 400 kèm lỗi từng ô, và header khi tải giấy tờ (QT-10); duyệt, từ chối, Chờ duyệt chỉ cho kiểm soát
 * viên. Các Service là mock; nghiệp vụ test ở TicketServiceTest, TicketReviewServiceTest, chạy thật trên database ở
 * TicketCreateIT, TicketReviewIT.
 */
@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, KeycloakRoleMapper.class, LoginSuccessHandler.class,
        KeycloakLogoutSuccessHandler.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class TicketControllerTest {

    private static final String BLOCK_REQUEST = """
            {"type": "PHONG_TOA", "accountNumber": "1020000050", "reason": "Khách báo mất thẻ", "confirmed": true}
            """;
    private static final byte[] PDF_CONTENT = "%PDF-1.7 noi dung gia".getBytes(StandardCharsets.US_ASCII);
    private static final String RESUBMIT_REQUEST = """
            {"reason": "Đã bổ sung chữ ký", "removedAttachmentIds": [3], "confirmed": true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketQueryService ticketQueryService;

    @MockitoBean
    private TicketReviewService ticketReviewService;

    @MockitoBean
    private TicketRevisionService ticketRevisionService;

    // SecurityConfig (bộ lọc phiên bị thu hồi) và LoginSuccessHandler cần EmployeeService.
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Chưa đăng nhập tạo phiếu thì 401 dạng JSON")
    void createReturns401WhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập

        // Chạy
        ResultActions result = mockMvc.perform(createRequest(BLOCK_REQUEST).with(csrf()));

        // Kiểm tra kết quả
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Admin gọi mọi API phiếu đều 403 (Admin không xem phiếu, QT-07)")
    void adminGets403Everywhere() throws Exception {
        // Chuẩn bị, chạy, kiểm tra kết quả
        mockMvc.perform(createRequest(BLOCK_REQUEST).with(csrf()).with(as("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/1").with(as("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/eligibility").param("type", "PHONG_TOA")
                .param("accountNumber", "1020000050").with(as("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(customerCheck().with(as("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Kiểm soát viên không tạo phiếu, không gọi eligibility, customer-check (403); xem chi tiết được")
    void checkerCannotCreateButCanView() throws Exception {
        // Chuẩn bị, chạy, kiểm tra kết quả
        mockMvc.perform(createRequest(BLOCK_REQUEST).with(csrf()).with(as("CHECKER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/eligibility").param("type", "PHONG_TOA")
                .param("accountNumber", "1020000050").with(as("CHECKER"))).andExpect(status().isForbidden());
        mockMvc.perform(customerCheck().with(as("CHECKER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/1").with(as("CHECKER"))).andExpect(status().isOk());
        verify(ticketService, never()).create(any(), any());
    }

    @Test
    @DisplayName("Chưa đăng nhập duyệt, từ chối, xem Chờ duyệt thì 401")
    void reviewApisReturn401WhenNotLoggedIn() throws Exception {
        // Chuẩn bị: không đăng nhập; chạy, kiểm tra kết quả
        mockMvc.perform(post("/api/tickets/7/approve").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(rejectRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/pending-review")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/pending-review/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Giao dịch viên và Admin không duyệt, từ chối, không xem Chờ duyệt (403, QT-07)")
    void makerAndAdminCannotReview() throws Exception {
        // Chuẩn bị, chạy, kiểm tra kết quả
        for (String role : new String[] {"MAKER", "ADMIN"}) {
            mockMvc.perform(post("/api/tickets/7/approve").with(csrf()).with(as(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(rejectRequest().with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/tickets/pending-review").with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/tickets/pending-review/summary").with(as(role)))
                    .andExpect(status().isForbidden());
        }
        verify(ticketReviewService, never()).approve(any());
        verify(ticketReviewService, never()).reject(any(), any());
    }

    @Test
    @DisplayName("Kiểm soát viên duyệt thì 204; từ chối thì 204 và Service nhận đúng lý do")
    void checkerApprovesAndRejects() throws Exception {
        // Chuẩn bị, chạy
        ResultActions approved = mockMvc.perform(post("/api/tickets/7/approve").with(csrf()).with(as("CHECKER")));
        ResultActions rejected = mockMvc.perform(rejectRequest().with(as("CHECKER")));

        // Kiểm tra kết quả
        approved.andExpect(status().isNoContent());
        rejected.andExpect(status().isNoContent());
        verify(ticketReviewService).approve(7L);
        verify(ticketReviewService).reject(7L, "Đơn chưa có chữ ký");
    }

    @Test
    @DisplayName("Từ chối không có lý do, hoặc lý do quá 500 ký tự, thì 400 kèm lỗi ô reason")
    void rejectWithoutValidReasonReturns400() throws Exception {
        // Chuẩn bị
        String blankReason = "{\"reason\": \"   \"}";
        String longReason = "{\"reason\": \"" + "x".repeat(501) + "\"}";

        // Chạy, kiểm tra kết quả
        for (String body : new String[] {blankReason, longReason}) {
            mockMvc.perform(post("/api/tickets/7/reject").with(csrf()).with(as("CHECKER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        }
        verify(ticketReviewService, never()).reject(any(), any());
    }

    @Test
    @DisplayName("Màn Chờ duyệt: loại phiếu lạ trên URL thì 400; hợp lệ thì Service nhận đúng mã và loại")
    void pendingReviewParsesFilters() throws Exception {
        // Chuẩn bị, chạy, kiểm tra kết quả
        mockMvc.perform(get("/api/tickets/pending-review").param("type", "XOA_TAI_KHOAN").with(as("CHECKER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets/pending-review").param("code", "pyc-2026").param("type", "PHONG_TOA")
                        .with(as("CHECKER")))
                .andExpect(status().isOk());
        verify(ticketReviewService).findPendingReview("pyc-2026", TicketType.PHONG_TOA);
    }

    @Test
    @DisplayName("Sửa và gửi lại, edit-data, hủy: chưa đăng nhập 401; Kiểm soát viên và Admin 403 (QT-07)")
    void revisionApisAreForMakerOnly() throws Exception {
        // Chuẩn bị, chạy, kiểm tra kết quả: chưa đăng nhập
        mockMvc.perform(resubmitRequest(RESUBMIT_REQUEST).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/7/edit-data")).andExpect(status().isUnauthorized());
        mockMvc.perform(cancelRequest("{\"reason\": \"Khách đổi ý\"}")).andExpect(status().isUnauthorized());
        // Sai role
        for (String role : new String[] {"CHECKER", "ADMIN"}) {
            mockMvc.perform(resubmitRequest(RESUBMIT_REQUEST).with(csrf()).with(as(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/tickets/7/edit-data").with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(cancelRequest("{\"reason\": \"Khách đổi ý\"}").with(as(role)))
                    .andExpect(status().isForbidden());
        }
        verify(ticketRevisionService, never()).resubmit(any(), any(), any());
        verify(ticketRevisionService, never()).cancel(any(), any());
    }

    @Test
    @DisplayName("Giao dịch viên gửi lại phiếu thì 204; hủy thì 204 với đúng lý do; hủy không có lý do thì 400")
    void makerResubmitsAndCancels() throws Exception {
        // Chuẩn bị, chạy
        ResultActions resubmitted = mockMvc.perform(resubmitRequest(RESUBMIT_REQUEST).with(csrf()).with(as("MAKER")));
        ResultActions cancelled = mockMvc.perform(cancelRequest("{\"reason\": \"Khách đổi ý\"}").with(as("MAKER")));
        ResultActions blank = mockMvc.perform(cancelRequest("{\"reason\": \" \"}").with(as("MAKER")));

        // Kiểm tra kết quả
        resubmitted.andExpect(status().isNoContent());
        cancelled.andExpect(status().isNoContent());
        blank.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        verify(ticketRevisionService).cancel(7L, "Khách đổi ý");
    }

    @Test
    @DisplayName("edit-data có Cache-Control no-store để trình duyệt không lưu hồ sơ khách đầy đủ (spec mục 11.2)")
    void editDataIsNotCached() throws Exception {
        // Chuẩn bị
        when(ticketRevisionService.findEditData(7L)).thenReturn(new TicketEditData(7L, "PYC-2026-0007",
                "MO_TAI_KHOAN", "PENDING_OPEN", "ACTIVE", "1030000001", 3L, 21L, null, "Lý do", "Thiếu chữ ký", 1,
                List.of()));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/tickets/7/edit-data").with(as("MAKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.rejectReason").value("Thiếu chữ ký"));
    }

    @Test
    @DisplayName("Tất cả phiếu: Giao dịch viên, Kiểm soát viên xem được, Admin 403; mã phiếu có ký tự lạ thì 400;"
            + " nhiều trạng thái cách nhau bằng dấu phẩy")
    void ticketListParsesFilters() throws Exception {
        // Chuẩn bị
        when(ticketQueryService.findPage(any())).thenReturn(new TicketListPage(List.of(), 0, Map.of()));

        // Chạy, kiểm tra kết quả
        mockMvc.perform(get("/api/tickets").with(as("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets").param("code", "PYC%").with(as("MAKER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets").param("statuses", "PENDING_APPROVAL,REJECTED")
                        .param("createdFrom", "2026-11-01").param("createdTo", "2026-11-07").with(as("CHECKER")))
                .andExpect(status().isOk());
        ArgumentCaptor<TicketListQuery> query = ArgumentCaptor.forClass(TicketListQuery.class);
        verify(ticketQueryService).findPage(query.capture());
        assertEquals(List.of(TicketStatus.PENDING_APPROVAL, TicketStatus.REJECTED), query.getValue().statuses());
        assertEquals(LocalDate.of(2026, 11, 7), query.getValue().createdTo());
    }

    @Test
    @DisplayName("Giao dịch viên gửi phiếu hợp lệ thì 201 kèm id, mã phiếu; trường chi nhánh lạ trong JSON bị bỏ qua")
    void makerCreatesTicket() throws Exception {
        // Chuẩn bị: JSON có thêm branchId như kẻ xấu tự chèn; DTO không có trường này (QT-04)
        when(ticketService.create(any(), any())).thenReturn(new TicketCreated(7L, "PYC-2026-0007"));
        String requestWithBranch = """
                {"type": "PHONG_TOA", "accountNumber": "1020000050", "reason": "Khách báo mất thẻ",
                 "confirmed": true, "branchId": 99}
                """;

        // Chạy
        ResultActions result = mockMvc.perform(createRequest(requestWithBranch).with(csrf()).with(as("MAKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.code").value("PYC-2026-0007"));
    }

    @Test
    @DisplayName("Dữ liệu sai: lý do quá 500 ký tự, CCCD 11 số, điện thoại sai, thiếu xác nhận, chọn cả 2 kiểu khách")
    void invalidDataReturns400WithFieldErrors() throws Exception {
        // Chuẩn bị
        String invalidRequest = """
                {"type": "MO_TAI_KHOAN", "customerId": 21, "accountTypeId": 1, "reason": "%s",
                 "newCustomer": {"fullName": "Đỗ Thị Bình", "dateOfBirth": "1990-03-15", "gender": "Nữ",
                   "cccd": "09919000000", "idIssueDate": "2021-04-10", "idIssuePlace": "Cục CS QLHC",
                   "phone": "0900-002", "email": "binh@nhdemo.local", "address": "12 Đường Giả"}}
                """.formatted("x".repeat(501));

        // Chạy
        ResultActions result = mockMvc.perform(createRequest(invalidRequest).with(csrf()).with(as("MAKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("reason", "newCustomer.cccd",
                        "newCustomer.phone", "confirmed", "customerChoiceValid")));
        verify(ticketService, never()).create(any(), any());
    }

    @Test
    @DisplayName("Loại phiếu lạ trong JSON thì 400")
    void unknownTicketTypeReturns400() throws Exception {
        // Chuẩn bị
        String request = """
                {"type": "XOA_TAI_KHOAN", "accountNumber": "1020000050", "reason": "Lý do", "confirmed": true}
                """;

        // Chạy, kiểm tra kết quả
        mockMvc.perform(createRequest(request).with(csrf()).with(as("MAKER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tải giấy tờ: đúng Content-Type đã kiểm lúc lưu, nosniff, tên gốc tiếng Việt mã hóa UTF-8 (QT-10)")
    void attachmentDownloadHasSafeHeaders() throws Exception {
        // Chuẩn bị
        when(ticketQueryService.readAttachment(7L, 3L)).thenReturn(
                new TicketQueryService.AttachmentContent("Giấy đề nghị.pdf", "application/pdf", PDF_CONTENT));

        // Chạy
        ResultActions result = mockMvc.perform(get("/api/tickets/7/attachments/3").param("download", "true")
                .with(as("CHECKER")));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''Gi%E1%BA%A5y%20%C4%91%E1%BB%81%20ngh%E1%BB%8B.pdf")));
    }

    private MockMultipartHttpServletRequestBuilder createRequest(String json) {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "cccd.pdf", "application/pdf", PDF_CONTENT);
        return multipart("/api/tickets").file(data).file(file);
    }

    /** PUT multipart: MockMvc chỉ có multipart() cho POST, nên đổi method thành PUT. */
    private MockMultipartHttpServletRequestBuilder resubmitRequest(String json) {
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/tickets/7").file(data);
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private MockHttpServletRequestBuilder cancelRequest(String json) {
        return post("/api/tickets/7/cancel").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockHttpServletRequestBuilder rejectRequest() {
        return post("/api/tickets/7/reject").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Đơn chưa có chữ ký\"}");
    }

    private MockHttpServletRequestBuilder customerCheck() {
        return post("/api/tickets/customer-check").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cccd\": \"099190000002\"}");
    }

    private RequestPostProcessor as(String role) {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}