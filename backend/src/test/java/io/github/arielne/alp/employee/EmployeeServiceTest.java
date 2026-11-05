package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.AuditNote;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh cho EmployeeService (spec admin mục 6): phiên đăng nhập còn dùng được không (bị khóa, hoặc đăng nhập
 * trước lần bị bỏ role gần nhất, thì phiên bị thu hồi), và cập nhật hồ sơ lúc đăng nhập kèm nội dung audit.
 */
class EmployeeServiceTest {

    private static final String KEYCLOAK_ID = "f862d39b-b598-4d60-9a20-ae0c70ac22bc";
    private static final OffsetDateTime REVOKED_AT = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.ofHours(7));

    private EmployeeMapper employeeMapper;
    private AuditNote auditNote;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        auditNote = new AuditNote();
        service = new EmployeeService(employeeMapper, auditNote);
    }

    @Test
    @DisplayName("Đăng nhập với email đã tự đổi trên KeyCloak: lưu email mới, audit ghi Email cũ → mới")
    void updateProfileFromLoginRecordsEmailChange() {
        // Chuẩn bị: hồ sơ còn email cũ
        Employee employee = new Employee();
        employee.setKeycloakId(KEYCLOAK_ID);
        employee.setEmail("khoa.cu@nhdemo.local");
        employee.setFullName("Trần Minh Khoa");

        // Chạy
        service.updateProfileFromLogin(employee, "khoa.tm@nhdemo.local", "Trần Minh Khoa");

        // Kiểm tra kết quả: chỉ email đổi nên mô tả nói đúng trường đó
        verify(employeeMapper).upsertFromLogin(KEYCLOAK_ID, "khoa.tm@nhdemo.local", "Trần Minh Khoa");
        assertEquals("khoa.tm@nhdemo.local", auditNote.getObjectId());
        assertEquals("Email: khoa.cu@nhdemo.local → khoa.tm@nhdemo.local", auditNote.getDescription());
        assertEquals(1, auditNote.getChanges().size());
    }

    @Test
    @DisplayName("Nhân viên bị khóa: phiên bị thu hồi")
    void lockedEmployeeIsRevoked() {
        // Chuẩn bị
        when(employeeMapper.findSessionStateByKeycloakId(KEYCLOAK_ID)).thenReturn(sessionState("LOCKED", null));

        // Chạy và kiểm tra kết quả
        assertTrue(service.isSessionRevoked(KEYCLOAK_ID, Instant.now()));
    }

    @Test
    @DisplayName("Phiên tạo trước lần bị bỏ role: bị thu hồi, vì phiên vẫn còn role vừa bị bỏ")
    void sessionCreatedBeforeRoleRemovalIsRevoked() {
        // Chuẩn bị: đăng nhập lúc 8 giờ 59, Admin bỏ role lúc 9 giờ
        when(employeeMapper.findSessionStateByKeycloakId(KEYCLOAK_ID)).thenReturn(sessionState("ACTIVE", REVOKED_AT));

        // Chạy và kiểm tra kết quả
        assertTrue(service.isSessionRevoked(KEYCLOAK_ID, REVOKED_AT.minusMinutes(1).toInstant()));
    }

    @Test
    @DisplayName("Phiên tạo sau lần bị bỏ role (đã đăng nhập lại, token có role mới): dùng được")
    void sessionCreatedAfterRoleRemovalIsValid() {
        // Chuẩn bị: Admin bỏ role lúc 9 giờ, người đó đăng nhập lại lúc 9 giờ 01
        when(employeeMapper.findSessionStateByKeycloakId(KEYCLOAK_ID)).thenReturn(sessionState("ACTIVE", REVOKED_AT));

        // Chạy và kiểm tra kết quả
        assertFalse(service.isSessionRevoked(KEYCLOAK_ID, REVOKED_AT.plusMinutes(1).toInstant()));
    }

    @Test
    @DisplayName("Chưa từng bị thu hồi, hoặc chưa có hồ sơ: phiên dùng được")
    void neverRevokedOrNoProfileIsValid() {
        // Chuẩn bị: Khoa chưa bị thu hồi lần nào; người còn lại chưa có hồ sơ (mapper trả null)
        when(employeeMapper.findSessionStateByKeycloakId(KEYCLOAK_ID)).thenReturn(sessionState("ACTIVE", null));

        // Chạy và kiểm tra kết quả
        assertFalse(service.isSessionRevoked(KEYCLOAK_ID, Instant.now()));
        assertFalse(service.isSessionRevoked("0a1b2c3d-0000-4000-8000-000000000099", Instant.now()));
    }

    private EmployeeSessionState sessionState(String status, OffsetDateTime sessionsInvalidBefore) {
        EmployeeSessionState state = new EmployeeSessionState();
        state.setStatus(status);
        state.setSessionsInvalidBefore(sessionsInvalidBefore);
        return state;
    }
}