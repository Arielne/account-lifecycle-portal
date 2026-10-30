package io.github.arielne.alp.audit;

import io.github.arielne.alp.common.AuditChange;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Một dòng của GET /api/audit-logs (CN-23). action và objectType là mã; React đổi sang nhãn tiếng Việt.
 * Không trả mã request và tên method (source): hai cột này chỉ để tra lỗi, không hiện trên màn.
 */
public record AuditLogItem(Long id, OffsetDateTime occurredAt, String employeeName, String action, String objectType,
                           String objectId, String objectName, String description, String ipAddress,
                           List<AuditChange> changes) {
}