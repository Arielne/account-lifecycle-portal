package io.github.arielne.alp.audit;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.RequestIdFilter;
import io.github.arielne.alp.common.TransactionConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi audit log bằng AOP (CN-26, spec admin mục 4). Service chỉ gắn @Audited và ghi nội dung vào AuditNote;
 * class này thêm phần chung (người làm, IP, mã request, nơi gọi) rồi lưu 1 dòng audit_log.
 * @Aspect: Spring bọc mỗi bean có method gắn @Audited bằng một proxy; gọi method đó là đi qua proxy,
 * và proxy chạy thêm phần việc ở đây. Thứ tự so với transaction xem TransactionConfig.
 */
@Aspect
@Component
@Order(TransactionConfig.AUDIT_ORDER)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditNote auditNote;
    private final AuditLogService auditLogService;

    /**
     * Chạy sau khi method gắn @Audited trả về bình thường; method ném lỗi thì không chạy, nên không có dòng audit.
     * "@annotation(audited)": chọn mọi method có @Audited và đưa annotation đó vào tham số audited.
     */
    @AfterReturning("@annotation(audited)")
    public void recordAuditLog(JoinPoint joinPoint, Audited audited) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setKeycloakId(currentKeycloakId());
        entry.setAction(audited.action());
        entry.setObjectType(audited.objectType());
        entry.setObjectId(auditNote.getObjectId());
        entry.setDescription(auditNote.getDescription());
        entry.setIpAddress(currentRequest().getRemoteAddr());
        entry.setRequestId(RequestIdFilter.currentRequestId());
        entry.setSource(joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName());
        auditLogService.record(entry, auditNote.getChanges());
    }

    /**
     * keycloak_id của người đang đăng nhập. Tên của Authentication là claim sub trong ID token
     * (user-name-attribute: sub trong application.yml), chính là keycloak_id.
     */
    private String currentKeycloakId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getName();
    }

    /** Request đang chạy. getRemoteAddr() đã là IP của người dùng: Spring đọc sẵn header X-Forwarded-For của Nginx. */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}