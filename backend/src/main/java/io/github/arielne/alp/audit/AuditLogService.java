package io.github.arielne.alp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.AuditChange;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu audit log (CN-26). Chỉ AuditAspect gọi vào đây; Service của module khác không gọi thẳng module audit.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * Lưu 1 dòng audit kèm các trường đổi giá trị.
     * MANDATORY: chỉ chạy bên trong transaction đang có của thao tác, không có thì báo lỗi ngay.
     * Nhờ vậy dòng audit không bao giờ nằm ở transaction riêng (spec admin mục 4, bước 4).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditLogEntry entry, List<AuditChange> changes) {
        if (!changes.isEmpty()) {
            entry.setChangesJson(toJson(changes));
        }
        auditLogMapper.insert(entry);
    }

    /** Danh sách thay đổi thành chuỗi JSON dạng [{"label": "...", "before": "...", "after": "..."}]. */
    private String toJson(List<AuditChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không đổi được danh sách thay đổi sang JSON", exception);
        }
    }
}