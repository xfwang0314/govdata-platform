package com.xfwang.platform.mcp.domain.service;

import com.xfwang.platform.mcp.domain.entity.AuthzAgentToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 网关限流器与配额守护器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpRateLimiter {

    private static final int DEFAULT_MINUTE_LIMIT = 60;
    private final ConcurrentHashMap<String, AtomicInteger> localMinuteCounters = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean allowed, String rejectReason) {}

    /**
     * 检查当前 Token 是否满足每分钟频次与每日总配额约束。
     */
    public RateLimitResult checkRateLimit(AuthzAgentToken token) {
        String tokenHash = token.getTokenHash();
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        // 1. 每日配额检查
        int maxDaily = token.getMaxDailyRequests() != null ? token.getMaxDailyRequests() : 2000;
        String dailyKey = "mcp:daily:" + tokenHash + ":" + today;
        try {
            long currentDaily = RedisUtils.incrAtomicValue(dailyKey);
            if (currentDaily == 1) {
                RedisUtils.expire(dailyKey, Duration.ofDays(2));
            }
            if (currentDaily > maxDaily) {
                return new RateLimitResult(false, "今日请求次数已达到上限（" + maxDaily + " 次）");
            }
        } catch (Exception e) {
            log.warn("Redis 记录每日配额异常，回退本地数据库配额判定: {}", e.getMessage());
            if (token.getUsedRequestsToday() != null && token.getUsedRequestsToday() >= maxDaily) {
                return new RateLimitResult(false, "今日请求次数已达到上限（" + maxDaily + " 次）");
            }
        }

        // 2. 每分钟频次限流 (默认 60次/分)
        long currentMinute = System.currentTimeMillis() / 60000;
        String minuteKey = "mcp:rate:" + tokenHash + ":" + currentMinute;
        try {
            long currentMinuteCount = RedisUtils.incrAtomicValue(minuteKey);
            if (currentMinuteCount == 1) {
                RedisUtils.expire(minuteKey, Duration.ofSeconds(120));
            }
            if (currentMinuteCount > DEFAULT_MINUTE_LIMIT) {
                return new RateLimitResult(false, "调用过于频繁，已触发限流（每分钟上限 " + DEFAULT_MINUTE_LIMIT + " 次）");
            }
        } catch (Exception e) {
            // 本地 fallback
            String localKey = tokenHash + ":" + currentMinute;
            int count = localMinuteCounters.computeIfAbsent(localKey, k -> new AtomicInteger(0)).incrementAndGet();
            if (count > DEFAULT_MINUTE_LIMIT) {
                return new RateLimitResult(false, "调用过于频繁，已触发限流（每分钟上限 " + DEFAULT_MINUTE_LIMIT + " 次）");
            }
        }

        return new RateLimitResult(true, null);
    }
}
