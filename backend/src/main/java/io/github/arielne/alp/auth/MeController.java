package io.github.arielne.alp.auth;

import io.github.arielne.alp.employee.Employee;
import io.github.arielne.alp.employee.EmployeeService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/me: người đang đăng nhập (CN-01 đến CN-05). React gọi một lần khi mở app.
 * Họ tên, email, role lấy từ session (ID token lúc đăng nhập); chi nhánh lấy từ bảng employee JOIN branch.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final EmployeeService employeeService;

    @GetMapping("/api/me")
    public MeResponse getCurrentUser(Authentication authentication) {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        Employee employee = employeeService.findByKeycloakId(user.getSubject());
        return new MeResponse(
                user.getSubject(),
                VietnameseFullName.fromToken(user),
                user.getEmail(),
                appRolesOf(authentication),
                branchOf(employee));
    }

    /** Role của app mà người này có, theo thứ tự MAKER, CHECKER, ADMIN; chưa có role thì danh sách rỗng. */
    private List<String> appRolesOf(Authentication authentication) {
        List<String> authorityNames = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorityNames.add(authority.getAuthority());
        }
        List<String> roles = new ArrayList<>();
        for (String appRole : KeycloakRoleMapper.APP_ROLES) {
            if (authorityNames.contains("ROLE_" + appRole)) {
                roles.add(appRole);
            }
        }
        return roles;
    }

    /** Chi nhánh của nhân viên; null khi chưa có hồ sơ hoặc chưa được gán chi nhánh (ví dụ Admin). */
    private BranchSummary branchOf(Employee employee) {
        if (employee == null || employee.getBranchId() == null) {
            return null;
        }
        return new BranchSummary(employee.getBranchId(), employee.getBranchCode(), employee.getBranchName());
    }

    /** JSON trả về cho React. */
    public record MeResponse(String keycloakId, String fullName, String email, List<String> roles,
                             BranchSummary branch) {
    }

    /** Chi nhánh rút gọn trong /api/me. */
    public record BranchSummary(Long id, String code, String name) {
    }
}