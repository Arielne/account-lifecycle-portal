package io.github.arielne.alp.common;

/**
 * Dạng lỗi chung trả cho React: mã HTTP và câu thông báo tiếng Việt.
 * Hiện dùng cho lỗi 401 của /api; class @RestControllerAdvice làm sau sẽ dùng lại dạng này.
 */
public record ApiErrorResponse(int status, String message) {
}