package io.github.arielne.alp.auth;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.employee.Employee;
import io.github.arielne.alp.employee.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Trả người đang đăng nhập kèm hồ sơ nhân viên cho các module nghiệp vụ (CLAUDE.md: module nào cũng được gọi auth
 * để lấy người đang đăng nhập). Tên của Authentication là claim sub trong ID token, chính là keycloak_id.
 */
@Service
@RequiredArgsConstructor
public class CurrentEmployeeService {

    private final EmployeeService employeeService;

    /** Không có hồ sơ (bình thường không xảy ra: hồ sơ được tạo ngay khi đăng nhập) thì 403. */
    public CurrentEmployee get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String keycloakId = authentication.getName();
        Employee employee = employeeService.findByKeycloakId(keycloakId);
        if (employee == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tài khoản chưa có hồ sơ nhân viên");
        }
        return new CurrentEmployee(employee.getId(), keycloakId, employee.getFullName(), employee.getBranchId(),
                employee.getBranchName());
    }
}