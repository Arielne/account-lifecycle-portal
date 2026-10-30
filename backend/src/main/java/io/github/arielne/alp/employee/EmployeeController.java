package io.github.arielne.alp.employee;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Nhân viên (CN-22, màn 3.4). Chỉ ADMIN gọi được (SecurityConfig, QT-07).
 * @AuthenticationPrincipal: Spring đưa người đang đăng nhập (lấy từ session) vào tham số, để Service chặn
 * Admin tự khóa hoặc tự đổi role của mình. Controller không tự bắt lỗi: ApiExceptionHandler trả lỗi dạng chung.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeManagementService employeeManagementService;

    @GetMapping
    public List<EmployeeListItem> findAll() {
        return employeeManagementService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody EmployeeCreateRequest request) {
        employeeManagementService.create(request);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        employeeManagementService.update(id, request);
    }

    @PutMapping("/{id}/roles")
    public void assignRoles(@PathVariable Long id, @Valid @RequestBody EmployeeRolesRequest request,
                            @AuthenticationPrincipal OidcUser currentUser) {
        employeeManagementService.assignRoles(id, request.roles(), currentUser.getSubject());
    }

    @PostMapping("/{id}/lock")
    public void lock(@PathVariable Long id, @AuthenticationPrincipal OidcUser currentUser) {
        employeeManagementService.lock(id, currentUser.getSubject());
    }

    @PostMapping("/{id}/unlock")
    public void unlock(@PathVariable Long id) {
        employeeManagementService.unlock(id);
    }
}