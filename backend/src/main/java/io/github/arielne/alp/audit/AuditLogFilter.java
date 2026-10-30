package io.github.arielne.alp.audit;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Điều kiện lọc và phân trang của màn Audit log (CN-23), truyền cho AuditLogMapper.
 * Trường để null (hoặc systemOnly = false) là không lọc theo điều kiện đó; file XML dùng <if> để bỏ qua.
 */
@Getter
@Setter
public class AuditLogFilter {

    /** Chỉ dòng do nhân viên này làm. */
    private Long employeeId;
    /** Chỉ dòng do hệ thống làm (employee_id rỗng), ví dụ job Ngủ đông. */
    private boolean systemOnly;
    /** Từ thời điểm này (tính cả). */
    private OffsetDateTime fromTime;
    /** Trước thời điểm này (không tính). */
    private OffsetDateTime toTime;
    private String action;
    private String objectType;
    /** Số dòng bỏ qua; kiểu long vì trang lớn nhân 10 vượt giới hạn của int. */
    private long offset;
    private int size;
}