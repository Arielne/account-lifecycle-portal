package io.github.arielne.alp.audit;

import java.util.List;

/** Một trang của màn Audit log (CN-23): tối đa 10 dòng, kèm tổng số dòng khớp điều kiện lọc để React vẽ phân trang. */
public record AuditLogPage(List<AuditLogItem> items, long total) {
}