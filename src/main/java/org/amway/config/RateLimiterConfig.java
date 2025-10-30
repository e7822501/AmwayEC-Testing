package org.amway.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimiterConfig {
    
    /**
     * 全局限流器：每秒最多 1000 次抽獎
     */
    @Bean
    public RateLimiter globalRateLimiter() {
        return RateLimiter.create(1000.0);
    }
    
    /**
     * 用戶維度限流器：每個用戶每秒最多 1 次
     */
    @Bean
    public ConcurrentHashMap<Long, RateLimiter> userRateLimiters() {
        return new ConcurrentHashMap<>();
    }
}
