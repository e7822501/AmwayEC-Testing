package org.amway.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "抽獎請求")
public class DrawRequest {

    @NotNull(message = "活動ID不能為空")
    @Schema(description = "活動ID", example = "1")
    private Long activityId;

    @NotNull(message = "抽獎次數不能為空")
    @Min(value = 1, message = "抽獎次數至少為1")
    @Schema(description = "抽獎次數", example = "1")
    private Integer drawCount;
}