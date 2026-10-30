package io.github.arielne.alp.audit;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Tham số lọc của GET /api/audit-logs (CN-23), ví dụ ?employeeId=4&from=2026-10-01&to=2026-10-31&page=2.
 * Tham số nào không gửi thì là null, tức không lọc theo điều kiện đó. system = true: chỉ dòng do hệ thống làm.
 * @DateTimeFormat(iso = DATE): ngày gửi lên dạng 2026-10-31.
 */
public record AuditLogQuery(
        Long employeeId,
        Boolean system,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        String action,
        String objectType,
        Integer page) {
}