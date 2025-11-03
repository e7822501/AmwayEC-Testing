package org.amway.aspect;

import com.google.common.util.concurrent.RateLimiter;
import org.amway.exception.BusinessException;
import org.amway.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiter globalRateLimiter;
    private final ConcurrentHashMap<Long, RateLimiter> userRateLimiters;
    @Qualifier("userQpsConfig")  // 用戶 QPS 配置（注入）
    private final Double userQpsConfig;

    @Around("@annotation(org.amway.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 全局限流
        if (!globalRateLimiter.tryAcquire()) {
            log.warn("全局限流觸發");
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "系統繁忙，請稍後再試");
        }

        // 2. 用戶維度限流
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Long userId = null;
            try {
                userId = Long.valueOf(authentication.getPrincipal().toString());
            } catch (Exception ignore) {}
            if (userId != null) {
                RateLimiter userRateLimiter = userRateLimiters.computeIfAbsent(
                        userId,
                        k -> RateLimiter.create(userQpsConfig)
                );
                if (!userRateLimiter.tryAcquire()) {
                    log.warn("用戶 {} 觸發限流", userId);
                    throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "請求過於頻繁，請稍後再試");
                }
            }
        }

        return joinPoint.proceed();
    }
}
