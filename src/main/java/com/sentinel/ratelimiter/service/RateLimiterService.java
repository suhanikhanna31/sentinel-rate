package com.sentinel.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Token Bucket Rate Limiter
 *
 * Each client gets a "bucket" of tokens. Tokens refill over time at a fixed rate.
 * Each API request consumes one token. When the bucket is empty, the request is denied.
 *
 * Redis keys per client:
 *   rate_limit:tokens:<clientId>      – current token count (float stored as string)
 *   rate_limit:last_refill:<clientId> – epoch-millis of the last refill timestamp
 */
@Service
public class RateLimiterService {

    /** Maximum tokens a bucket can hold (burst capacity). */
    public static final long MAX_TOKENS = 10;

    /** Tokens added per second (sustained throughput = REFILL_RATE * 60 req/min). */
    private static final double REFILL_RATE = 2.0; // 2 tokens/sec → 120 req/min

    /** TTL for idle buckets – cleaned up after 10 minutes of inactivity. */
    private static final Duration BUCKET_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempt to consume one token from the client's bucket.
     *
     * @param clientId unique identifier for the calling client (e.g. IP address)
     * @return remaining tokens after this request, or -1 if denied (bucket empty)
     */
    public long isAllowed(String clientId) {
        String tokenKey      = "rate_limit:tokens:"      + clientId;
        String lastRefillKey = "rate_limit:last_refill:" + clientId;

        long nowMillis = Instant.now().toEpochMilli();

        String rawTokens     = redisTemplate.opsForValue().get(tokenKey);
        String rawLastRefill = redisTemplate.opsForValue().get(lastRefillKey);

        double currentTokens;
        long   lastRefillMillis;

        if (rawTokens == null || rawLastRefill == null) {
            // First request from this client – start with a full bucket
            currentTokens    = MAX_TOKENS;
            lastRefillMillis = nowMillis;
        } else {
            currentTokens    = Double.parseDouble(rawTokens);
            lastRefillMillis = Long.parseLong(rawLastRefill);
        }

        // Refill: add tokens proportional to time elapsed since last request
        double elapsedSeconds = (nowMillis - lastRefillMillis) / 1000.0;
        currentTokens = Math.min(MAX_TOKENS, currentTokens + elapsedSeconds * REFILL_RATE);

        if (currentTokens < 1.0) {
            // Bucket is empty – persist partially-refilled state and deny
            persist(tokenKey, lastRefillKey, currentTokens, nowMillis);
            return -1;
        }

        currentTokens -= 1.0;
        persist(tokenKey, lastRefillKey, currentTokens, nowMillis);
        return (long) currentTokens;
    }

    private void persist(String tokenKey, String lastRefillKey,
                         double tokens, long timestampMillis) {
        redisTemplate.opsForValue().set(tokenKey,      String.valueOf(tokens),         BUCKET_TTL);
        redisTemplate.opsForValue().set(lastRefillKey, String.valueOf(timestampMillis), BUCKET_TTL);
    }
}