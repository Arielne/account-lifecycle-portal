package io.github.arielne.alp.audit;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis cho bảng app.audit_log (CN-26 ghi, CN-23 xem). Câu SQL nằm trong AuditLogMapper.xml.
 * Chỉ có thêm và đọc; không có hàm sửa hay xóa audit log (QT-09).
 */
@Mapper
public interface AuditLogMapper {

    /** Thêm 1 dòng audit, thời gian lấy theo giờ của database. */
    void insert(AuditLogEntry entry);

    /** Một trang dòng audit khớp điều kiện lọc, mới nhất ở trên. */
    List<AuditLogRow> findPage(AuditLogFilter filter);

    /** Tổng số dòng khớp điều kiện lọc (không tính phân trang). */
    long count(AuditLogFilter filter);
}