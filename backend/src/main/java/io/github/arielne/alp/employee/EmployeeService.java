package io.github.arielne.alp.employee;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Nghiệp vụ hồ sơ nhân viên (module employee).
 * Module auth gọi vào đây sau khi đăng nhập và khi trả GET /api/me; employee không gọi ngược lại auth.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    /** Thêm hoặc cập nhật hồ sơ theo thông tin trong ID token, gọi sau mỗi lần đăng nhập thành công. */
    public void saveProfileFromLogin(String keycloakId, String email, String fullName) {
        employeeMapper.upsertFromLogin(keycloakId, email, fullName);
    }

    /** Hồ sơ kèm chi nhánh; null khi chưa có. */
    public Employee findByKeycloakId(String keycloakId) {
        return employeeMapper.findByKeycloakId(keycloakId);
    }
}