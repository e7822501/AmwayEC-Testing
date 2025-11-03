package org.amway.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimiterConfig {

    @Value("${rate-limit.global-qps:1000}")
    private double globalQps;

    @Value("${rate-limit.user-qps:1}")
    private double userQps;

    /**
     * 全局限流器（QPS 支援動態配置）
     */
    @Bean
    public RateLimiter globalRateLimiter() {
        return RateLimiter.create(globalQps);
    }

    /**
     * 用戶維度限流器（每個用戶 QPS 支援動態配置）
     */
    @Bean
    public ConcurrentHashMap<Long, RateLimiter> userRateLimiters() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 用戶單次最大 QPS 配置（供切面調用）
     */
    @Bean("userQpsConfig")
    public Double userQpsConfig() {
        return userQps;
    }
}
