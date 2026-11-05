package io.github.arielne.alp.employee;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ hồ sơ nhân viên (module employee).
 * Module auth gọi vào đây sau khi đăng nhập và khi trả GET /api/me; employee không gọi ngược lại auth.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final AuditNote auditNote;

    /** Thêm hoặc cập nhật hồ sơ theo thông tin trong ID token, gọi sau mỗi lần đăng nhập thành công. */
    public void saveProfileFromLogin(String keycloakId, String email, String fullName) {
        employeeMapper.upsertFromLogin(keycloakId, email, fullName);
    }

    /** Hồ sơ kèm chi nhánh; null khi chưa có. */
    public Employee findByKeycloakId(String keycloakId) {
        return employeeMapper.findByKeycloakId(keycloakId);
    }

    /**
     * Lần đăng nhập đầu của người tự đăng ký: tạo hồ sơ Chờ cấp quyền và ghi audit "Tự đăng ký"
     * (người làm là chính người đó, spec admin mục 4).
     */
    @Transactional
    @Audited(action = "SELF_REGISTER", objectType = "EMPLOYEE")
    public void registerFromFirstLogin(String keycloakId, String email, String fullName) {
        employeeMapper.upsertFromLogin(keycloakId, email, fullName);
        auditNote.setObjectId(email);
        auditNote.setDescription("Chờ cấp quyền");
    }

    /**
     * Email trong token đã thuộc hồ sơ của người khác (keycloak_id khác) chưa. Có thì không được lưu hồ sơ:
     * câu INSERT sẽ vi phạm UNIQUE của cột email (spec admin mục 6, lỗi email trùng khi đăng nhập).
     */
    public boolean isEmailUsedByOtherEmployee(String email, String keycloakId) {
        Employee owner = employeeMapper.findByEmail(email.toLowerCase(Locale.ROOT));
        return owner != null && !owner.getKeycloakId().equals(keycloakId);
    }

    /**
     * Phiên đăng nhập này đã bị thu hồi chưa; bộ lọc ở module auth gọi mỗi request vào /api.
     * Bị thu hồi khi nhân viên đã bị khóa, hoặc phiên được tạo trước lần thu hồi gần nhất (Admin bỏ role, khóa):
     * role trong phiên là role lúc đăng nhập, nên phiên cũ vẫn còn role vừa bị bỏ.
     * sessionCreatedAt và mốc thu hồi đều lấy theo đồng hồ của backend, nên so được với nhau.
     */
    public boolean isSessionRevoked(String keycloakId, Instant sessionCreatedAt) {
        EmployeeSessionState state = employeeMapper.findSessionStateByKeycloakId(keycloakId);
        if (state == null) {
            return false;
        }
        if ("LOCKED".equals(state.getStatus())) {
            return true;
        }
        OffsetDateTime invalidBefore = state.getSessionsInvalidBefore();
        return invalidBefore != null && sessionCreatedAt.isBefore(invalidBefore.toInstant());
    }
}