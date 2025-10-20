package org.amway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "活動響應")
public class ActivityResponse {
    
    @Schema(description = "活動ID")
    private Long id;
    
    @Schema(description = "活動名稱")
    private String name;
    
    @Schema(description = "活動描述")
    private String description;
    
    @Schema(description = "開始時間")
    private LocalDateTime startTime;
    
    @Schema(description = "結束時間")
    private LocalDateTime endTime;
    
    @Schema(description = "每人最多抽獎次數")
    private Integer maxDrawsPerUser;
    
    @Schema(description = "活動狀態")
    private String status;
    
    @Schema(description = "獎品列表")
    private List<PrizeResponse> prizes;
}