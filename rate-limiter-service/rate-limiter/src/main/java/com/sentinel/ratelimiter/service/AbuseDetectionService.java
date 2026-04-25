package com.sentinel.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Adaptive Abuse Detection Service
 *
 * Monitors each client for anomalous traffic patterns and dynamically throttles
 * (blocks) abusive clients.
 *
 * Strategy:
 *   – Each rate-limit violation is counted in a short rolling window (5 min).
 *   – Once a client exceeds MAX_VIOLATIONS in that window, they are hard-blocked
 *     for BLOCK_DURATION.
 *   – The block duration doubles on repeat offences (adaptive throttling).
 *
 * Redis keys:
 *   abuse:count:<clientId>       – violation count in the rolling window
 *   abuse:block:<clientId>       – present when client is hard-blocked
 *   abuse:offence_count:<clientId> – number of times this client has been blocked
 */
@Service
public class AbuseDetectionService {

    /** Violations within VIOLATION_WINDOW before a hard block is issued. */
    private static final int MAX_VIOLATIONS = 3;

    /** Rolling window for counting violations. */
    private static final Duration VIOLATION_WINDOW = Duration.ofMinutes(5);

    /** Base block duration – doubles with each repeated offence (adaptive). */
    private static final Duration BASE_BLOCK_DURATION = Duration.ofMinutes(10);

    /** Hard cap on block duration regardless of offence count. */
    private static final Duration MAX_BLOCK_DURATION = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public AbuseDetectionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return true if the client is currently hard-blocked
     */
    public boolean isBlocked(String clientId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("abuse:block:" + clientId));
    }

    /**
     * Record one rate-limit violation for the given client.
     * If violations exceed the threshold, the client is dynamically blocked.
     *
     * @param clientId the offending client identifier
     */
    public void recordViolation(String clientId) {
        String violationKey = "abuse:count:" + clientId;

        Long violationCount = redisTemplate.opsForValue().increment(violationKey);

        // Start the rolling window on the first violation
        if (violationCount != null && violationCount == 1) {
            redisTemplate.expire(violationKey, VIOLATION_WINDOW);
        }

        // Threshold crossed – issue an adaptive block
        if (violationCount != null && violationCount >= MAX_VIOLATIONS) {
            applyBlock(clientId);
        }
    }

    // ---- helpers ----

    /**
     * Block the client. Duration doubles with each repeat offence up to MAX_BLOCK_DURATION.
     */
    private void applyBlock(String clientId) {
        String offenceKey = "abuse:offence_count:" + clientId;
        Long offenceCount = redisTemplate.opsForValue().increment(offenceKey);
        // Keep the offence history for a long time so repeat offenders keep getting longer bans
        redisTemplate.expire(offenceKey, Duration.ofDays(7));

        long blockMinutes = BASE_BLOCK_DURATION.toMinutes();
        if (offenceCount != null && offenceCount > 1) {
            // Exponential back-off: 10 min, 20 min, 40 min, … capped at MAX_BLOCK_DURATION
            blockMinutes = Math.min(
                    BASE_BLOCK_DURATION.toMinutes() * (1L << (offenceCount - 1)),
                    MAX_BLOCK_DURATION.toMinutes()
            );
        }

        redisTemplate.opsForValue().set(
                "abuse:block:" + clientId,
                "BLOCKED_OFFENCE_" + offenceCount,
                Duration.ofMinutes(blockMinutes)
        );
    }
}