package io.github.arielne.alp.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PSQLException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
    // Câu báo cho các constraint hay bị vi phạm khi 2 người lưu cùng lúc (tên do Postgres đặt hoặc ghi trong V3, V5).
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "customer_cccd_key", "Số CCCD đã có hồ sơ khách hàng",
            "uq_ticket_open_per_account", "Tài khoản đang có phiếu khác chờ duyệt hoặc bị từ chối");

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

    /** File quá 5 MB hoặc cả request quá 26 MB (spring.servlet.multipart): 400 nói rõ giới hạn. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception,
                                                                         HttpHeaders headers, HttpStatusCode status,
                                                                         WebRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(),
                "Mỗi file tối đa 5 MB, gửi tối đa 5 file");
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Database chặn vì trùng UNIQUE hoặc vướng khóa ngoại, khi hai người lưu cùng lúc nên lọt qua bước kiểm tra
     * của Service. Trả 409 thay vì 500. Log chỉ ghi SQLState và tên constraint, không ghi message của Postgres:
     * message đó có thể chứa giá trị của dòng dữ liệu, ví dụ số CCCD bị trùng (spec phiếu, mục 11.3).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String constraintName = constraintNameOf(exception);
        log.warn("Database từ chối thao tác ghi: SQLState {}, constraint {}", sqlStateOf(exception), constraintName);
        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.CONFLICT.value(), messageForConstraint(constraintName));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Tên constraint Postgres báo vi phạm (PSQLException của driver Postgres); null khi không đọc được. */
    private String constraintNameOf(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof PSQLException postgresException
                && postgresException.getServerErrorMessage() != null) {
            return postgresException.getServerErrorMessage().getConstraint();
        }
        return null;
    }

    /** Mã lỗi chuẩn SQL (ví dụ 23505 là trùng UNIQUE); null khi lỗi không đến từ JDBC. */
    private String sqlStateOf(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof SQLException sqlException) {
            return sqlException.getSQLState();
        }
        return null;
    }

    /** Constraint đã biết thì đổi sang câu nghiệp vụ; còn lại giữ câu chung. */
    private String messageForConstraint(String constraintName) {
        if (constraintName != null && CONSTRAINT_MESSAGES.containsKey(constraintName)) {
            return CONSTRAINT_MESSAGES.get(constraintName);
        }
        return "Dữ liệu vừa bị người khác thay đổi. Vui lòng tải lại trang rồi thử lại.";
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