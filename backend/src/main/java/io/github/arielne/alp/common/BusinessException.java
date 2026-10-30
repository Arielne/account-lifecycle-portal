package io.github.arielne.alp.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ dùng chung (spec admin mục 3): mã trùng, danh mục đang dùng nên không xóa được, không tìm thấy...
 * Service ném lỗi này kèm mã HTTP và câu tiếng Việt nói rõ lý do; ApiExceptionHandler đổi thành JSON dạng chung.
 * Kế thừa RuntimeException nên không phải khai báo throws, và transaction tự hủy khi lỗi bay ra khỏi Service.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}