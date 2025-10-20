package org.amway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "單次抽獎結果")
public class DrawResult {
    
    @Schema(description = "抽獎記錄ID")
    private Long recordId;
    
    @Schema(description = "是否中獎")
    private Boolean isWinning;
    
    @Schema(description = "獎品ID")
    private Long prizeId;
    
    @Schema(description = "獎品名稱")
    private String prizeName;
    
    @Schema(description = "獎品類型")
    private String prizeType;
    
    @Schema(description = "獎品描述")
    private String prizeDescription;
    
    @Schema(description = "抽獎時間")
    private LocalDateTime drawTime;
}