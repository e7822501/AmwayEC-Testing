package org.amway.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登入請求")
public class LoginRequest {

    @NotBlank(message = "用戶名不能為空")
    @Schema(description = "用戶名", example = "user1")
    private String username;

    @NotBlank(message = "密碼不能為空")
    @Schema(description = "密碼", example = "password123")
    private String password;
}