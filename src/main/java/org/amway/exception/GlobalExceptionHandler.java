package org.amway.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.amway.exception.enums.ErrorCode;
import org.amway.exception.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String traceId = generateTraceId();
        log.warn("[{}] 參數驗證失敗: {}", traceId, errors);

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .errorCode(ErrorCode.INVALID_PARAMETER.getCode())
                .message("參數驗證失敗")
                .details(errors)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();

        return ResponseEntity
                .status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(response);
    }

    /**
     * 業務異常統一處理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("[{}] 業務異常: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }

    /**
     * JWT 認證失敗
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("[{}] 認證失敗: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .errorCode(ErrorCode.UNAUTHORIZED.getCode())
                .message("用戶名或密碼錯誤")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();

        return ResponseEntity
                .status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(response);
    }

    /**
     * 系統異常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("[{}] 系統異常", traceId, ex);

        // 生產環境不暴露詳細錯誤信息
        String message = isProduction() ? "系統異常，請稍後再試" : ex.getMessage();

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .errorCode(ErrorCode.INTERNAL_ERROR.getCode())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(response);
    }

    /**
     * 生成追蹤 ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 判斷是否生產環境
     */
    private boolean isProduction() {
        String env = System.getProperty("spring.profiles.active", "dev");
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
    }
}
