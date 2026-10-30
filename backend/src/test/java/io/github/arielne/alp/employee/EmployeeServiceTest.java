package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.arielne.alp.common.AuditNote;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh cho việc quyết định phiên đăng nhập còn dùng được không (spec admin mục 6):
 * bị khóa, hoặc phiên tạo trước lần bị bỏ role gần nhất, thì phiên bị thu hồi.
 */
class EmployeeServiceTest {

    private static final String KEYCLOAK_ID = "f862d39b-b598-4d60-9a20-ae0c70ac22bc";
    private static final OffsetDateTime REVOKED_AT = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.ofHours(7));

    private EmployeeMapper employeeMapper;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        service = new EmployeeService(employeeMapper, new AuditNote());
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