package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.notification.AccountActivationMailer;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Test nhanh cho nghiệp vụ màn Nhân viên (spec admin mục 8, test 4–7). Không dựng Spring:
 * EmployeeMapper, KeycloakUserClient, AccountActivationMailer là mock của Mockito (CI không có KeyCloak, Mailpit).
 */
class EmployeeManagementServiceTest {

    private static final String ADMIN_KEYCLOAK_ID = "927c941a-3244-4299-8a41-7c78b44d08e6";
    private static final String NEW_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000e1";

    private EmployeeMapper employeeMapper;
    private KeycloakUserClient keycloakUserClient;
    private AccountActivationMailer accountActivationMailer;
    private AuditNote auditNote;
    private EmployeeManagementService service;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        keycloakUserClient = mock(KeycloakUserClient.class);
        accountActivationMailer = mock(AccountActivationMailer.class);
        auditNote = new AuditNote();
        service = new EmployeeManagementService(employeeMapper, keycloakUserClient, accountActivationMailer, auditNote);
    }

    @Test
    @DisplayName("Thêm nhân viên trùng email đã có hồ sơ thì báo 409, không tạo user trên KeyCloak")
    void createWithDuplicateEmailThrowsConflict() {
        // Chuẩn bị: email đã thuộc hồ sơ khác
        when(employeeMapper.findByEmail("lan.nt@nhdemo.local")).thenReturn(employee(1L, "x", null));
        EmployeeCreateRequest request = new EmployeeCreateRequest("Nguyễn Thị Lan", "Lan.NT@nhdemo.local", 4L,
                List.of("MAKER"));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        // Kiểm tra kết quả: email được đổi sang chữ thường trước khi so
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Email đã có hồ sơ nhân viên", exception.getMessage());
        verify(keycloakUserClient, never()).createUser(anyString(), any());
    }

    @Test
    @DisplayName("Tạo trên KeyCloak xong mà lưu hồ sơ lỗi thì xóa user vừa tạo trên KeyCloak")
    void createDeletesKeycloakUserWhenSavingProfileFails() {
        // Chuẩn bị: KeyCloak tạo được user, database từ chối câu INSERT
        when(employeeMapper.findBranchNameById(4L)).thenReturn("Chi nhánh Tân Phú");
        when(keycloakUserClient.createUser(anyString(), any())).thenReturn(NEW_KEYCLOAK_ID);
        doThrow(new DataIntegrityViolationException("lỗi lưu hồ sơ")).when(employeeMapper).insert(any());
        EmployeeCreateRequest request = new EmployeeCreateRequest("Nguyễn Văn An", "an.nv@nhdemo.local", 4L,
                List.of("MAKER"));

        // Chạy
        assertThrows(DataIntegrityViolationException.class, () -> service.create(request));

        // Kiểm tra kết quả
        verify(keycloakUserClient).deleteUser(NEW_KEYCLOAK_ID);
        verify(keycloakUserClient, never()).sendSetPasswordEmail(anyString());
    }

    @Test
    @DisplayName("Thêm nhân viên hợp lệ: tách Họ, Tên đúng kiểu Việt, gán role, gửi mail đặt mật khẩu")
    void createSplitsNameAndSendsSetPasswordEmail() {
        // Chuẩn bị
        when(keycloakUserClient.createUser(anyString(), any())).thenReturn(NEW_KEYCLOAK_ID);
        EmployeeCreateRequest request = new EmployeeCreateRequest("  Nguyễn  Thị   Lan ", "lan.moi@nhdemo.local", null,
                List.of("ADMIN"));

        // Chạy
        service.create(request);

        // Kiểm tra kết quả: Admin không cần chi nhánh
        verify(keycloakUserClient).createUser("lan.moi@nhdemo.local", new NameParts("Nguyễn Thị", "Lan"));
        verify(keycloakUserClient).addRoles(NEW_KEYCLOAK_ID, List.of("ADMIN"));
        verify(keycloakUserClient).sendSetPasswordEmail(NEW_KEYCLOAK_ID);
        assertEquals("Nguyễn Thị Lan", auditNote.getDescription());
    }

    @Test
    @DisplayName("Gán Giao dịch viên cho người chưa có chi nhánh thì báo 409, không gọi KeyCloak")
    void assignMakerWithoutBranchThrowsConflict() {
        // Chuẩn bị: người tự đăng ký, chưa có chi nhánh
        when(employeeMapper.findById(7L)).thenReturn(employee(7L, NEW_KEYCLOAK_ID, null));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(7L, List.of("MAKER"), ADMIN_KEYCLOAK_ID));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(keycloakUserClient, never()).addRoles(anyString(), any());
    }

    @Test
    @DisplayName("Admin tự đổi role của mình thì báo 409")
    void assignRolesToSelfThrowsConflict() {
        // Chuẩn bị: hồ sơ của chính Admin đang đăng nhập
        when(employeeMapper.findById(4L)).thenReturn(employee(4L, ADMIN_KEYCLOAK_ID, null));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(4L, List.of("ADMIN", "CHECKER"), ADMIN_KEYCLOAK_ID));

        // Kiểm tra kết quả
        assertEquals("Không thể tự đổi role của chính mình", exception.getMessage());
        verify(keycloakUserClient, never()).addRoles(anyString(), any());
    }

    @Test
    @DisplayName("Admin tự khóa mình thì báo 409, không đổi trạng thái")
    void lockSelfThrowsConflict() {
        // Chuẩn bị
        when(employeeMapper.findById(4L)).thenReturn(employee(4L, ADMIN_KEYCLOAK_ID, null));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.lock(4L, ADMIN_KEYCLOAK_ID));

        // Kiểm tra kết quả
        assertEquals("Không thể tự khóa chính mình", exception.getMessage());
        verify(employeeMapper, never()).updateStatus(any(), any());
        verify(keycloakUserClient, never()).setEnabled(anyString(), any(Boolean.class));
    }

    @Test
    @DisplayName("Gán role đầu tiên cho người Chờ cấp quyền thì gửi mail kích hoạt")
    void firstRoleSendsActivationMail() {
        // Chuẩn bị: người tự đăng ký, đã được gán chi nhánh, chưa có role
        when(employeeMapper.findById(7L)).thenReturn(employee(7L, NEW_KEYCLOAK_ID, 4L));
        when(employeeMapper.findBranchNameById(4L)).thenReturn("Chi nhánh Tân Phú");
        when(keycloakUserClient.findAppRoles(NEW_KEYCLOAK_ID)).thenReturn(List.of());

        // Chạy
        service.assignRoles(7L, List.of("MAKER"), ADMIN_KEYCLOAK_ID);

        // Kiểm tra kết quả
        verify(keycloakUserClient).addRoles(NEW_KEYCLOAK_ID, List.of("MAKER"));
        verify(accountActivationMailer).sendAccountActivated("thu.dangky@nhdemo.local", "Bắc Đẩu Thu",
                "Giao dịch viên");
        assertEquals("Thêm Giao dịch viên", auditNote.getDescription());
        // Chỉ thêm role, không bỏ role nào: phiên đang mở không bị thu hồi.
        verify(employeeMapper, never()).updateSessionsInvalidBefore(any(), any());
    }

    @Test
    @DisplayName("Bỏ role thì thu hồi phiên đang mở, sau khi KeyCloak đã bỏ role")
    void removingRoleRevokesOpenSessions() {
        // Chuẩn bị: đang giữ cả Giao dịch viên và Kiểm soát viên, Admin bỏ Kiểm soát viên
        when(employeeMapper.findById(7L)).thenReturn(employee(7L, NEW_KEYCLOAK_ID, 4L));
        when(employeeMapper.findBranchNameById(4L)).thenReturn("Chi nhánh Tân Phú");
        when(keycloakUserClient.findAppRoles(NEW_KEYCLOAK_ID)).thenReturn(List.of("MAKER", "CHECKER"));
        OffsetDateTime before = OffsetDateTime.now();

        // Chạy
        service.assignRoles(7L, List.of("MAKER"), ADMIN_KEYCLOAK_ID);

        // Kiểm tra kết quả: thời điểm thu hồi lấy sau khi KeyCloak bỏ role, để phiên đăng nhập xen giữa cũng bị đẩy ra
        InOrder inOrder = inOrder(keycloakUserClient, employeeMapper);
        inOrder.verify(keycloakUserClient).removeRoles(NEW_KEYCLOAK_ID, List.of("CHECKER"));
        ArgumentCaptor<OffsetDateTime> revokedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        inOrder.verify(employeeMapper).updateSessionsInvalidBefore(eq(7L), revokedAt.capture());
        assertFalse(revokedAt.getValue().isBefore(before));
    }

    @Test
    @DisplayName("Thêm role cho người đã có role thì không gửi mail kích hoạt; bỏ role cũ được ghi vào mô tả")
    void changingExistingRolesSendsNoMail() {
        // Chuẩn bị: đang là Giao dịch viên, đổi sang Kiểm soát viên
        when(employeeMapper.findById(7L)).thenReturn(employee(7L, NEW_KEYCLOAK_ID, 4L));
        when(employeeMapper.findBranchNameById(4L)).thenReturn("Chi nhánh Tân Phú");
        when(keycloakUserClient.findAppRoles(NEW_KEYCLOAK_ID)).thenReturn(List.of("MAKER"));

        // Chạy
        service.assignRoles(7L, List.of("CHECKER"), ADMIN_KEYCLOAK_ID);

        // Kiểm tra kết quả
        verify(keycloakUserClient).addRoles(NEW_KEYCLOAK_ID, List.of("CHECKER"));
        verify(keycloakUserClient).removeRoles(NEW_KEYCLOAK_ID, List.of("MAKER"));
        verify(accountActivationMailer, never()).sendAccountActivated(anyString(), anyString(), anyString());
        assertEquals("Thêm Kiểm soát viên, Bỏ Giao dịch viên", auditNote.getDescription());
    }

    @Test
    @DisplayName("Khóa nhân viên: hồ sơ LOCKED, tắt tài khoản và đăng xuất mọi phiên trên KeyCloak")
    void lockDisablesAccountAndLogsOutOnKeycloak() {
        // Chuẩn bị
        when(employeeMapper.findById(7L)).thenReturn(employee(7L, NEW_KEYCLOAK_ID, 4L));

        // Chạy
        service.lock(7L, ADMIN_KEYCLOAK_ID);

        // Kiểm tra kết quả
        verify(employeeMapper).updateStatus(7L, "LOCKED");
        verify(keycloakUserClient).setEnabled(NEW_KEYCLOAK_ID, false);
        verify(keycloakUserClient).logout(NEW_KEYCLOAK_ID);
        // Thu hồi luôn phiên đang mở: mở khóa trước khi người đó gọi API thì phiên cũ cũng không dùng lại được.
        verify(employeeMapper).updateSessionsInvalidBefore(eq(7L), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Bỏ chi nhánh của người đang là Kiểm soát viên thì báo 409")
    void removeBranchOfCheckerThrowsConflict() {
        // Chuẩn bị: đang ở Chi nhánh Tân Phú, là Kiểm soát viên
        Employee checker = employee(7L, NEW_KEYCLOAK_ID, 4L);
        checker.setBranchName("Chi nhánh Tân Phú");
        when(employeeMapper.findById(7L)).thenReturn(checker);
        when(keycloakUserClient.findAppRoles(NEW_KEYCLOAK_ID)).thenReturn(List.of("CHECKER"));
        EmployeeUpdateRequest request = new EmployeeUpdateRequest("Bắc Đẩu Thu", "thu.dangky@nhdemo.local", null);

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, request));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(employeeMapper, never()).updateProfile(any());
    }

    private Employee employee(Long id, String keycloakId, Long branchId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setKeycloakId(keycloakId);
        employee.setEmail("thu.dangky@nhdemo.local");
        employee.setFullName("Bắc Đẩu Thu");
        employee.setBranchId(branchId);
        employee.setStatus("ACTIVE");
        return employee;
    }
}