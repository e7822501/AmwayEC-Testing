package org.amway.exception.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 統一錯誤響應格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private Boolean success;
    
    /**
     * 業務錯誤碼（如 INSUFFICIENT_DRAWS）
     */
    private String errorCode;
    
    /**
     * 錯誤訊息
     */
    private String message;
    
    /**
     * 詳細信息（可選，如參數驗證錯誤的欄位列表）
     */
    private Object details;
    
    /**
     * 請求路徑
     */
    private String path;
    
    /**
     * 時間戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 追蹤 ID（用於日誌排查）
     */
    private String traceId;
}
