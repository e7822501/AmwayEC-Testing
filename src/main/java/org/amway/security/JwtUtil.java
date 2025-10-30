package org.amway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:3600000}") // 1小時
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}") // 7天
    private Long refreshTokenExpiration;

    private final RedisTemplate<String, Object> redisTemplate;

    public JwtUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(String username, Long userId, String role) {
        return generateToken(username, userId, role, accessTokenExpiration, "ACCESS");
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(String username, Long userId, String role) {
        String refreshToken = generateToken(username, userId, role, refreshTokenExpiration, "REFRESH");

        // 將 Refresh Token 存入 Redis 白名單
        String redisKey = "refresh_token:" + userId;
        redisTemplate.opsForValue().set(redisKey, refreshToken, refreshTokenExpiration, TimeUnit.MILLISECONDS);

        return refreshToken;
    }

    private String generateToken(String username, Long userId, String role, Long expiration, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("type", type);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    /**
     * 驗證 Token（檢查黑名單）
     */
    public boolean validateToken(String token, String username) {
        // 檢查是否在黑名單中
        if (isTokenBlacklisted(token)) {
            return false;
        }

        return (username.equals(extractUsername(token)) && !isTokenExpired(token));
    }

    /**
     * 驗證 Refresh Token（檢查白名單）
     */
    public boolean validateRefreshToken(String refreshToken, Long userId) {
        String redisKey = "refresh_token:" + userId;
        String storedToken = (String) redisTemplate.opsForValue().get(redisKey);

        return refreshToken.equals(storedToken) && !isTokenExpired(refreshToken);
    }

    /**
     * 將 Token 加入黑名單（用戶登出或修改密碼）
     */
    public void blacklistToken(String token) {
        Claims claims = extractClaims(token);
        Date expiration = claims.getExpiration();
        long ttl = expiration.getTime() - System.currentTimeMillis();

        if (ttl > 0) {
            String redisKey = "blacklist:" + token;
            redisTemplate.opsForValue().set(redisKey, "1", ttl, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 檢查 Token 是否在黑名單中
     */
    public boolean isTokenBlacklisted(String token) {
        String redisKey = "blacklist:" + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * 刪除 Refresh Token（登出）
     */
    public void revokeRefreshToken(Long userId) {
        String redisKey = "refresh_token:" + userId;
        redisTemplate.delete(redisKey);
    }
}
