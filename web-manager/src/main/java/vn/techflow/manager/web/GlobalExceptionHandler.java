package vn.techflow.manager.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.task.TaskNotFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", request, fields);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication(AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này", request, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên", request, Map.of());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ApiError> taskNotFound(TaskNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Dữ liệu hoặc tham số không đúng định dạng", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Dữ liệu xung đột với bản ghi hiện có", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure on {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp lỗi. Vui lòng thử lại.", request, Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request,
                                               Map<String, String> validation) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message == null ? status.getReasonPhrase() : message, request.getRequestURI(), validation);
        return ResponseEntity.status(status).body(body);
    }
}
