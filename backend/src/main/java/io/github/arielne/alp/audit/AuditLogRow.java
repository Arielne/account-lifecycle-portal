package io.github.arielne.alp.audit;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng audit_log MyBatis đọc ra cho màn Audit log (CN-23), kèm họ tên người làm và tên nhân viên là đối tượng.
 * Cột changes (jsonb) đọc dưới dạng chuỗi JSON; AuditLogService đổi sang danh sách AuditChange.
 */
@Getter
@Setter
public class AuditLogRow {

    private Long id;
    private OffsetDateTime occurredAt;
    // Rỗng khi hành động do hệ thống làm.
    private String employeeName;
    private String action;
    private String objectType;
    private String objectId;
    // Họ tên hiện tại của nhân viên là đối tượng (object_type EMPLOYEE, theo object_employee_id); loại khác thì rỗng.
    private String objectName;
    private String description;
    private String ipAddress;
    private String changesJson;
}