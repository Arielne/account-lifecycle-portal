package io.github.arielne.alp.common;

import java.util.List;

/**
 * Dạng lỗi chung của mọi API trong /api (spec admin mục 3): mã HTTP, câu thông báo tiếng Việt
 * và lỗi của từng ô khi dữ liệu không qua Bean Validation. React đọc dạng này để hiện lỗi.
 * Dùng ở ApiExceptionHandler (lỗi từ Controller) và ở 2 handler 401, 403 của Spring Security.
 */
public record ApiErrorResponse(int status, String message, List<FieldError> fieldErrors) {

    /** Lỗi không gắn với ô nào: fieldErrors là danh sách rỗng. */
    public ApiErrorResponse(int status, String message) {
        this(status, message, List.of());
    }

    /** Lỗi của một ô: tên trường (trùng tên trong JSON gửi lên) và câu báo lỗi. */
    public record FieldError(String field, String message) {
    }
}