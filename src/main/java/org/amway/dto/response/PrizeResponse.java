package org.amway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "獎品響應")
public class PrizeResponse {
    
    @Schema(description = "獎品ID")
    private Long id;
    
    @Schema(description = "獎品名稱")
    private String name;
    
    @Schema(description = "獎品描述")
    private String description;
    
    @Schema(description = "總庫存")
    private Integer totalStock;
    
    @Schema(description = "剩餘庫存")
    private Integer remainingStock;
    
    @Schema(description = "中獎機率")
    private BigDecimal probability;
    
    @Schema(description = "獎品類型")
    private String prizeType;
    
    @Schema(description = "圖片URL")
    private String imageUrl;
}