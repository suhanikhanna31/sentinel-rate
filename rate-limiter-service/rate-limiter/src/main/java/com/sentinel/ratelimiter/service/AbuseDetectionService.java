package com.sentinel.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AbuseDetectionService {

    private final StringRedisTemplate     redisTemplate;
    private final EventPersistenceService persistenceService;

    private static final int      TIER_1_THRESHOLD = 3;
    private static final int      TIER_2_THRESHOLD = 6;
    private static final int      TIER_3_THRESHOLD = 10;
    private static final Duration TIER_1_BLOCK     = Duration.ofMinutes(10);
    private static final Duration TIER_2_BLOCK     = Duration.ofHours(1);
    private static final Duration TIER_3_BLOCK     = Duration.ofHours(24);
    private static final Duration VIOLATION_TTL    = Duration.ofMinutes(10);

    public AbuseDetectionService(StringRedisTemplate redisTemplate,
                                 EventPersistenceService persistenceService) {
        this.redisTemplate     = redisTemplate;
        this.persistenceService = persistenceService;
    }

    public boolean isBlocked(String clientId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("abuse:block:" + clientId));
    }

    public long recordViolation(String clientId) {
        String countKey = "abuse:count:" + clientId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == null) count = 1L;
        redisTemplate.expire(countKey, VIOLATION_TTL);

        Duration blockDuration = resolveBlockDuration(count);
        if (blockDuration != null) {
            redisTemplate.opsForValue().set("abuse:block:" + clientId, "BLOCKED", blockDuration);
        }

        persistenceService.saveViolation(clientId, count);
        return count;
    }

    public void unblock(String clientId) {
        redisTemplate.delete("abuse:block:" + clientId);
        redisTemplate.delete("abuse:count:" + clientId);
    }

    private Duration resolveBlockDuration(long violations) {
        if (violations >= TIER_3_THRESHOLD) return TIER_3_BLOCK;
        if (violations >= TIER_2_THRESHOLD) return TIER_2_BLOCK;
        if (violations >= TIER_1_THRESHOLD) return TIER_1_BLOCK;
        return null;
    }
}