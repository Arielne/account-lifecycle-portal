package io.github.arielne.alp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.AuditChange;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu audit log (CN-26) và đọc cho màn Audit log (CN-23). Hàm lưu chỉ AuditAspect gọi;
 * Service của module khác không gọi thẳng module audit. Không có hàm sửa hay xóa (QT-09).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int PAGE_SIZE = 10;
    // Khoảng thời gian người dùng chọn là ngày theo giờ Việt Nam; cột occurred_at lưu kèm múi giờ.
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /** Một trang 10 dòng khớp điều kiện lọc, mới nhất ở trên, kèm tổng số dòng để vẽ phân trang. */
    public AuditLogPage findPage(AuditLogQuery query) {
        AuditLogFilter filter = toFilter(query);
        List<AuditLogItem> items = new ArrayList<>();
        for (AuditLogRow row : auditLogMapper.findPage(filter)) {
            items.add(toItem(row));
        }
        return new AuditLogPage(items, auditLogMapper.count(filter));
    }

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

    /** Khoảng ngày [from, to] đổi thành [0 giờ ngày from, 0 giờ ngày sau to) theo giờ Việt Nam; trang nhỏ hơn 1 tính là trang 1. */
    private AuditLogFilter toFilter(AuditLogQuery query) {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setEmployeeId(query.employeeId());
        filter.setSystemOnly(Boolean.TRUE.equals(query.system()));
        if (query.from() != null) {
            filter.setFromTime(query.from().atStartOfDay(VIETNAM).toOffsetDateTime());
        }
        if (query.to() != null) {
            filter.setToTime(query.to().plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
        }
        filter.setAction(emptyToNull(query.action()));
        filter.setObjectType(emptyToNull(query.objectType()));
        int page = 1;
        if (query.page() != null && query.page() > 1) {
            page = query.page();
        }
        filter.setOffset((page - 1) * PAGE_SIZE);
        filter.setSize(PAGE_SIZE);
        return filter;
    }

    private AuditLogItem toItem(AuditLogRow row) {
        return new AuditLogItem(row.getId(), row.getOccurredAt(), row.getEmployeeName(), row.getAction(),
                row.getObjectType(), row.getObjectId(), row.getObjectName(), row.getDescription(),
                row.getIpAddress(), fromJson(row.getChangesJson()));
    }

    /** Chuỗi JSON của cột changes thành danh sách AuditChange; cột rỗng thì danh sách rỗng. */
    private List<AuditChange> fromJson(String changesJson) {
        if (changesJson == null) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(changesJson, AuditChange[].class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cột changes của audit_log không phải JSON hợp lệ", exception);
        }
    }

    private String emptyToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text;
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