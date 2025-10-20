package org.amway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "抽獎響應")
public class DrawResponse {
    
    @Schema(description = "抽獎結果列表")
    private List<DrawResult> results;
    
    @Schema(description = "本次抽獎次數")
    private Integer drawCount;
    
    @Schema(description = "剩餘可抽獎次數")
    private Integer remainingDraws;
}