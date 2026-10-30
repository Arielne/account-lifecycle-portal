package io.github.arielne.alp.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method ghi dữ liệu của Service cần ghi audit log (CN-26, spec admin mục 4).
 * Method chạy xong không lỗi thì AuditAspect (module audit) lưu 1 dòng audit_log với action và objectType ở đây,
 * cộng nội dung Service đã ghi vào AuditNote. Ví dụ: @Audited(action = "UPDATE", objectType = "BRANCH").
 * @Retention(RUNTIME): annotation còn lại lúc chạy để AOP đọc được; @Target(METHOD): chỉ gắn lên method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Mã hành động lưu vào audit_log.action, ví dụ CREATE, UPDATE, DELETE. */
    String action();

    /** Mã loại đối tượng lưu vào audit_log.object_type, ví dụ ACCOUNT_TYPE, BRANCH. */
    String objectType();
}