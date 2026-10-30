package io.github.arielne.alp.common;

/**
 * Một trường đổi giá trị trong thao tác sửa, lưu vào cột audit_log.changes (JSON) để màn Audit log hiện bảng
 * Trước và Sau. Nhãn và giá trị đều là chữ hiển thị (ví dụ "Hoạt động"), không phải mã (ACTIVE).
 */
public record AuditChange(String label, String before, String after) {
}