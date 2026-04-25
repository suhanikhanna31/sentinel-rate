package com.sentinel.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Tracks rate-limit violations per client and applies exponentially escalating
 * block durations to persistent abusers.
 *
 * <p>Block schedule (cumulative violations → block duration):
 * <ul>
 *   <li>3+ violations → 10-minute block</li>
 *   <li>6+ violations → 1-hour block</li>
 *   <li>10+ violations → 24-hour block</li>
 * </ul>
 *
 * <p>Key schema:
 * <ul>
 *   <li>{@code abuse:count:<clientId>}  – violation counter, TTL 10 min</li>
 *   <li>{@code abuse:block:<clientId>}  – presence = blocked, TTL = block duration</li>
 * </ul>
 */
@Service
public class AbuseDetectionService {

    private final StringRedisTemplate redisTemplate;

    private static final int      TIER_1_THRESHOLD = 3;
    private static final int      TIER_2_THRESHOLD = 6;
    private static final int      TIER_3_THRESHOLD = 10;

    private static final Duration TIER_1_BLOCK     = Duration.ofMinutes(10);
    private static final Duration TIER_2_BLOCK     = Duration.ofHours(1);
    private static final Duration TIER_3_BLOCK     = Duration.ofHours(24);

    /** How long the violation counter itself lives before resetting. */
    private static final Duration VIOLATION_TTL    = Duration.ofMinutes(10);

    public AbuseDetectionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns {@code true} if the client is currently in a hard block period.
     */
    public boolean isBlocked(String clientId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("abuse:block:" + clientId));
    }

    /**
     * Records one violation for {@code clientId} and applies a block if the
     * client has crossed a threshold.
     *
     * @return the updated violation count
     */
    public long recordViolation(String clientId) {
        String countKey = "abuse:count:" + clientId;

        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == null) count = 1L;

        // Refresh violation-counter TTL on every hit
        redisTemplate.expire(countKey, VIOLATION_TTL);

        // Apply the appropriate block tier
        Duration blockDuration = resolveBlockDuration(count);
        if (blockDuration != null) {
            redisTemplate.opsForValue()
                    .set("abuse:block:" + clientId, "BLOCKED", blockDuration);
        }

        return count;
    }

    /**
     * Manually lifts a block (useful for an admin unblock endpoint).
     */
    public void unblock(String clientId) {
        redisTemplate.delete("abuse:block:" + clientId);
        redisTemplate.delete("abuse:count:" + clientId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Duration resolveBlockDuration(long violations) {
        if (violations >= TIER_3_THRESHOLD) return TIER_3_BLOCK;
        if (violations >= TIER_2_THRESHOLD) return TIER_2_BLOCK;
        if (violations >= TIER_1_THRESHOLD) return TIER_1_BLOCK;
        return null;
    }
}
