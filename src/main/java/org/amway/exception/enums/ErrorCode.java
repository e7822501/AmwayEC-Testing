package org.amway.exception.enums;

/**
 * 業務錯誤碼枚舉
 */
public enum ErrorCode {

    // 4xx Client Errors
    INVALID_PARAMETER(400, "INVALID_PARAMETER", "參數驗證失敗"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "未登入或 Token 無效"),
    FORBIDDEN(403, "FORBIDDEN", "權限不足"),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "資源不存在"),

    // 422 業務邏輯錯誤
    INSUFFICIENT_DRAWS(422, "INSUFFICIENT_DRAWS", "剩餘抽獎次數不足"),
    ACTIVITY_NOT_ACTIVE(422, "ACTIVITY_NOT_ACTIVE", "活動未開始或已結束"),
    PRIZE_OUT_OF_STOCK(422, "PRIZE_OUT_OF_STOCK", "獎品庫存不足"),
    RATE_LIMIT_EXCEEDED(429, "RATE_LIMIT_EXCEEDED", "請求過於頻繁，請稍後再試"),

    // 5xx Server Errors
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "系統異常，請稍後再試"),
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "服務暫時不可用"),
    DATABASE_ERROR(500, "DATABASE_ERROR", "數據庫連線異常"),
    REDIS_ERROR(503, "REDIS_ERROR", "快取服務異常");

    private final int httpStatus;
    private final String code;
    private final String message;

    ErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
