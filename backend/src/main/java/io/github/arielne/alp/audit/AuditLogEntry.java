package io.github.arielne.alp.audit;

import lombok.Getter;
import lombok.Setter;

/**
 * Dữ liệu một dòng audit_log cần lưu (CN-26), truyền cho AuditLogMapper.insert.
 * Không có employee_id: câu INSERT tự tìm id nhân viên theo keycloakId của người đang đăng nhập.
 * Không có object_employee_id: đối tượng là nhân viên thì câu INSERT tự tìm id theo email trong objectId.
 * changesJson là danh sách AuditChange đã đổi sang chuỗi JSON; rỗng khi thao tác không phải sửa.
 */
@Getter
@Setter
public class AuditLogEntry {

    private String keycloakId;
    private String action;
    private String objectType;
    private String objectId;
    private String description;
    private String ipAddress;
    private String changesJson;
    private String requestId;
    private String source;
}