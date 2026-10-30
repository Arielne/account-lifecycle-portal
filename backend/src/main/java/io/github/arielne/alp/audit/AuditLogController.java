package io.github.arielne.alp.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Audit log (CN-23, màn 3.5): chỉ đọc, chỉ ADMIN gọi được (SecurityConfig, QT-07).
 * Không có API sửa hay xóa audit log (QT-09): class này chỉ có một hàm GET.
 * @ModelAttribute: Spring ghép các tham số trên URL (?employeeId=...&from=...) vào record AuditLogQuery.
 */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/api/audit-logs")
    public AuditLogPage findPage(@ModelAttribute AuditLogQuery query) {
        return auditLogService.findPage(query);
    }
}