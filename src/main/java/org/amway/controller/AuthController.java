package org.amway.controller;

import org.amway.dto.request.LoginRequest;
import org.amway.dto.request.RefreshTokenRequest;
import org.amway.dto.response.ApiResponse;
import org.amway.entity.User;
import org.amway.exception.BusinessException;
import org.amway.exception.enums.ErrorCode;
import org.amway.repository.UserRepository;
import org.amway.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "認證管理", description = "用戶登入、登出、Token 刷新")
public class AuthController {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    @PostMapping("/login")
    @Operation(summary = "用戶登入", description = "返回 Access Token 和 Refresh Token")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用戶名或密碼錯誤"));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用戶名或密碼錯誤");
        }
        
        String accessToken = jwtUtil.generateAccessToken(
            user.getUsername(), 
            user.getId(), 
            user.getRole().name()
        );
        
        String refreshToken = jwtUtil.generateRefreshToken(
            user.getUsername(), 
            user.getId(), 
            user.getRole().name()
        );
        
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole().name());
        
        return ApiResponse.success("登入成功", data);
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 獲取新的 Access Token")
    public ApiResponse<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        Long userId = jwtUtil.extractUserId(refreshToken);
        String username = jwtUtil.extractUsername(refreshToken);
        String role = jwtUtil.extractRole(refreshToken);
        
        // 驗證 Refresh Token
        if (!jwtUtil.validateRefreshToken(refreshToken, userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 無效或已過期");
        }
        
        // 生成新的 Access Token
        String newAccessToken = jwtUtil.generateAccessToken(username, userId, role);
        
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        
        return ApiResponse.success("Token 刷新成功", data);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "用戶登出", description = "將當前 Token 加入黑名單並撤銷 Refresh Token")
    public ApiResponse<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        
        // 提取 Token
        String token = authHeader.substring(7);
        
        // 加入黑名單
        jwtUtil.blacklistToken(token);
        
        // 撤銷 Refresh Token
        jwtUtil.revokeRefreshToken(userId);
        
        return ApiResponse.success("登出成功", null);
    }
}
