package com.sentinel.ratelimiter.service;

import com.sentinel.ratelimiter.config.RateLimitDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Sliding-window rate limiter backed by a Redis sorted set + Lua script.
 *
 * <p>Uses a single atomic Lua script so that the ZREMRANGEBYSCORE → ZADD → ZCARD
 * sequence is never split across two requests, eliminating the race condition
 * present in the previous counter-increment approach.
 *
 * <p>Key schema: {@code rate_limit:<clientId>}
 *   - Sorted set where score = epoch-millis of each request timestamp
 *   - Entries older than the window are pruned on every call (O(log n + k))
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Atomic sliding-window script.
     *
     * KEYS[1] = rate_limit:<clientId>
     * ARGV[1] = current epoch-millis (string)
     * ARGV[2] = window start epoch-millis (current - window)
     * ARGV[3] = max requests
     * ARGV[4] = window size in ms  (used as TTL for the sorted set)
     *
     * Returns: {allowed (0|1), current_count}
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key       = KEYS[1]
            local now       = tonumber(ARGV[1])
            local win_start = tonumber(ARGV[2])
            local limit     = tonumber(ARGV[3])
            local win_ms    = tonumber(ARGV[4])

            -- 1. Evict timestamps outside the window
            redis.call('ZREMRANGEBYSCORE', key, 0, win_start)

            -- 2. Count remaining
            local count = redis.call('ZCARD', key)

            if count < limit then
                -- 3. Record this request (score = timestamp, member = timestamp for uniqueness)
                redis.call('ZADD', key, now, now .. '-' .. math.random(1, 1000000))
                -- 4. Reset TTL so the key expires naturally after the window
                redis.call('PEXPIRE', key, win_ms)
                return {1, count + 1}
            else
                return {0, count}
            end
            """;

    private final RedisScript<List<Long>> script;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(SLIDING_WINDOW_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);
    }

    /**
     * Checks whether {@code clientId} is within the default rate limit
     * ({@link RateLimitDefaults#DEFAULT_MAX_REQUESTS} requests per
     * {@link RateLimitDefaults#DEFAULT_WINDOW_SECONDS}s window).
     *
     * @param clientId opaque client identifier (IP, API-key, user-id, …)
     * @return {@link RateLimitDecision} containing allowed flag and current count
     */
    public RateLimitDecision isAllowed(String clientId) {
        return isAllowed(clientId, RateLimitDefaults.DEFAULT_MAX_REQUESTS, RateLimitDefaults.DEFAULT_WINDOW_SECONDS);
    }

    /**
     * Checks whether {@code clientId} is within a caller-supplied rate limit. This is what
     * powers per-endpoint overrides: {@link com.sentinel.ratelimiter.aspect.RateLimitAspect}
     * calls this directly with the values from a method's {@code @RateLimit} annotation,
     * while {@link com.sentinel.ratelimiter.filter.RateLimitFilter} calls the single-arg
     * {@link #isAllowed(String)} overload for the global default.
     *
     * @param clientId      opaque client identifier (IP, API-key, user-id, …)
     * @param maxRequests   maximum requests allowed inside the rolling window
     * @param windowSeconds rolling window size, in seconds
     * @return {@link RateLimitDecision} containing allowed flag and current count
     */
    public RateLimitDecision isAllowed(String clientId, int maxRequests, int windowSeconds) {
        long windowMs = Duration.ofSeconds(windowSeconds).toMillis();
        long now      = System.currentTimeMillis();
        long winStart = now - windowMs;

        List<Long> result = redisTemplate.execute(
                script,
                Collections.singletonList("rate_limit:" + clientId),
                String.valueOf(now),
                String.valueOf(winStart),
                String.valueOf(maxRequests),
                String.valueOf(windowMs)
        );

        boolean allowed = result != null && result.get(0) == 1L;
        long    count   = result != null ? result.get(1) : maxRequests;

        return new RateLimitDecision(allowed, (int) Math.max(0, maxRequests - count));
    }

    /** Value object returned to callers so they can populate response headers. */
    public record RateLimitDecision(boolean allowed, int remaining) {}
}
