package io.github.arielne.alp.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Xử lý lỗi tập trung cho mọi Controller (spec admin mục 3); Controller không tự bắt lỗi.
 * @RestControllerAdvice: Controller nào ném lỗi thì Spring tìm method @ExceptionHandler hợp với loại lỗi ở đây,
 * giá trị trả về thành JSON. Kế thừa ResponseEntityExceptionHandler để nhận sẵn các lỗi chuẩn của Spring MVC
 * (sai JSON, sai kiểu tham số, đường dẫn không có...) kèm đúng mã HTTP; class này chỉ đổi thân lỗi sang dạng chung.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String SYSTEM_ERROR_MESSAGE = "Lỗi hệ thống. Vui lòng thử lại sau.";

    /** Dữ liệu không qua Bean Validation (@Valid trên DTO): 400 kèm lỗi của từng ô. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()));
        }
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), "Dữ liệu chưa hợp lệ",
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Các lỗi chuẩn còn lại của Spring MVC: giữ mã HTTP Spring đã chọn, thay thân lỗi bằng dạng chung. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Lỗi hệ thống của Spring MVC", exception);
        }
        ApiErrorResponse errorBody = new ApiErrorResponse(statusCode.value(), messageForStatus(statusCode));
        return ResponseEntity.status(statusCode).headers(headers).body(errorBody);
    }

    /** Lỗi nghiệp vụ do Service ném: mã HTTP và câu báo lỗi lấy từ BusinessException. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception) {
        ApiErrorResponse body = new ApiErrorResponse(exception.getStatus().value(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    /**
     * Database chặn vì trùng UNIQUE hoặc vướng khóa ngoại, khi hai người lưu cùng lúc nên lọt qua bước kiểm tra
     * của Service. Trả 409 thay vì 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Database từ chối thao tác ghi: {}", exception.getMostSpecificCause().getMessage());
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.CONFLICT.value(),
                "Dữ liệu vừa bị người khác thay đổi. Vui lòng tải lại trang rồi thử lại.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Lỗi không lường trước: ghi log đầy đủ (kèm mã request), chỉ trả câu chung, không lộ chi tiết ra ngoài. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception,
                                                                      HttpServletRequest request) {
        log.error("Lỗi không lường trước ở {} {}", request.getMethod(), request.getRequestURI(), exception);
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), SYSTEM_ERROR_MESSAGE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String messageForStatus(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            return "Không tìm thấy đường dẫn.";
        }
        if (statusCode.is4xxClientError()) {
            return "Yêu cầu không hợp lệ.";
        }
        return SYSTEM_ERROR_MESSAGE;
    }
}