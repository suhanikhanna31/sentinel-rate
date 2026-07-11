package com.sentinel.ratelimiter;

import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.EventPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AbuseDetectionService} in isolation, with Redis and
 * {@link EventPersistenceService} mocked out. These complement the Redis-backed
 * scenarios already covered in {@link RateLimiterIntegrationTest} by pinning down
 * the tier-boundary logic and the exact keys/durations written to Redis.
 */
@ExtendWith(MockitoExtension.class)
class AbuseDetectionServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private EventPersistenceService persistenceService;

    private AbuseDetectionService abuseDetectionService;

    @BeforeEach
    void setUp() {
        abuseDetectionService = new AbuseDetectionService(redisTemplate, persistenceService);
    }

    @Test
    @DisplayName("isBlocked returns true only when the block key exists")
    void isBlockedReflectsRedisKeyPresence() {
        when(redisTemplate.hasKey("abuse:block:client-a")).thenReturn(true);
        when(redisTemplate.hasKey("abuse:block:client-b")).thenReturn(false);

        assertThat(abuseDetectionService.isBlocked("client-a")).isTrue();
        assertThat(abuseDetectionService.isBlocked("client-b")).isFalse();
    }

    @Test
    @DisplayName("isBlocked treats a null Redis response as not blocked")
    void isBlockedHandlesNullGracefully() {
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        assertThat(abuseDetectionService.isBlocked("client-c")).isFalse();
    }

    @Test
    @DisplayName("recordViolation increments the counter, sets a TTL, and persists the count")
    void recordViolationIncrementsAndPersists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-d")).thenReturn(1L);

        long count = abuseDetectionService.recordViolation("client-d");

        assertThat(count).isEqualTo(1L);
        verify(redisTemplate).expire("abuse:count:client-d", Duration.ofMinutes(10));
        verify(persistenceService).saveViolation("client-d", 1L);
    }

    @Test
    @DisplayName("recordViolation treats a null increment result as the first violation")
    void recordViolationHandlesNullIncrement() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-e")).thenReturn(null);

        long count = abuseDetectionService.recordViolation("client-e");

        assertThat(count).isEqualTo(1L);
        verify(persistenceService).saveViolation("client-e", 1L);
    }

    @Test
    @DisplayName("Below the tier-1 threshold, no block key is written")
    void belowThresholdDoesNotBlock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-f")).thenReturn(2L);

        abuseDetectionService.recordViolation("client-f");

        verify(valueOperations, never()).set(eq("abuse:block:client-f"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Tier 1 threshold (3 violations) blocks for 10 minutes")
    void tierOneBlocksForTenMinutes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-g")).thenReturn(3L);

        abuseDetectionService.recordViolation("client-g");

        verify(valueOperations).set("abuse:block:client-g", "BLOCKED", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("Tier 2 threshold (6 violations) blocks for 1 hour")
    void tierTwoBlocksForOneHour() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-h")).thenReturn(6L);

        abuseDetectionService.recordViolation("client-h");

        verify(valueOperations).set("abuse:block:client-h", "BLOCKED", Duration.ofHours(1));
    }

    @Test
    @DisplayName("Tier 3 threshold (10 violations) blocks for 24 hours")
    void tierThreeBlocksForTwentyFourHours() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("abuse:count:client-i")).thenReturn(10L);

        abuseDetectionService.recordViolation("client-i");

        verify(valueOperations).set("abuse:block:client-i", "BLOCKED", Duration.ofHours(24));
    }

    @Test
    @DisplayName("unblock deletes both the block key and the violation counter")
    void unblockDeletesBothKeys() {
        abuseDetectionService.unblock("client-j");

        verify(redisTemplate).delete("abuse:block:client-j");
        verify(redisTemplate).delete("abuse:count:client-j");
    }
}
