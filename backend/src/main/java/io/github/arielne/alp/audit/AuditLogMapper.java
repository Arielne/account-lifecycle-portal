package io.github.arielne.alp.audit;

import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis cho bảng app.audit_log (CN-26). Câu SQL nằm trong AuditLogMapper.xml.
 * Chỉ có thêm dòng; không có hàm sửa hay xóa audit log (QT-09).
 */
@Mapper
public interface AuditLogMapper {

    /** Thêm 1 dòng audit, thời gian lấy theo giờ của database. */
    void insert(AuditLogEntry entry);
}