package io.github.arielne.alp.audit;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.RequestIdFilter;
import io.github.arielne.alp.common.TransactionConfig;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
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
 * Bước hệ thống không có request và người đăng nhập (ví dụ notification hoàn tất phiếu từ luồng đọc Kafka): người
 * làm, IP, mã request để rỗng, màn Audit log hiện "Hệ thống" (spec phiếu T3).
 */
@Aspect
@Component
@Order(TransactionConfig.AUDIT_ORDER)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditNote auditNote;
    private final AuditLogService auditLogService;

    /**
     * Bọc quanh method gắn @Audited: mở bản ghi chú mới (AuditNote.begin), chạy method (proceed), method trả về bình
     * thường thì lưu 1 dòng audit; method ném lỗi thì lỗi đi tiếp ra ngoài, không có dòng audit. finally trả lại bản
     * ghi chú cũ dù method thành công hay lỗi.
     * "@annotation(audited)": chọn mọi method có @Audited và đưa annotation đó vào tham số audited.
     */
    @Around("@annotation(audited)")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditNote.Content previous = auditNote.begin();
        try {
            Object result = joinPoint.proceed();
            saveEntry(joinPoint, audited);
            return result;
        } finally {
            auditNote.end(previous);
        }
    }

    private void saveEntry(JoinPoint joinPoint, Audited audited) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setKeycloakId(currentKeycloakId());
        entry.setAction(audited.action());
        entry.setObjectType(audited.objectType());
        entry.setObjectId(auditNote.getObjectId());
        entry.setDescription(auditNote.getDescription());
        entry.setIpAddress(currentIpAddress());
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

    /**
     * IP của người dùng; null khi không có request (bước hệ thống). getRemoteAddr() đã là IP của người dùng: Spring
     * đọc sẵn header X-Forwarded-For của Nginx.
     */
    private String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}