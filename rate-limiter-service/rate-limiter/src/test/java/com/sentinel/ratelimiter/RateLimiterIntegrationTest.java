package com.sentinel.ratelimiter;

import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.RateLimiterService;
import com.sentinel.ratelimiter.service.RateLimiterService.RateLimitDecision;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the rate-limiter and abuse-detection services.
 *
 * <p>Uses the embedded Redis configured in {@code application-test.properties}
 * (or the testcontainers Redis started by the Spring Boot test slice).
 * Each test operates on a unique clientId to guarantee isolation.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RateLimiterIntegrationTest {

    @Autowired private RateLimiterService    rateLimiterService;
    @Autowired private AbuseDetectionService abuseDetectionService;
    @Autowired private StringRedisTemplate   redisTemplate;

    /** Flush relevant keys before every test for isolation. */
    @BeforeEach
    void cleanRedis() {
        var keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("abuse:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    // ── RateLimiterService ────────────────────────────────────────────────────

    @Test
    @DisplayName("First request is allowed and remaining count decreases")
    void firstRequestIsAllowed() {
        RateLimitDecision d = rateLimiterService.isAllowed("test-client-1");
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isLessThan(100);
    }

    @Test
    @DisplayName("Requests within limit are all allowed")
    void requestsWithinLimitAllAllowed() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiterService.isAllowed("test-client-2").allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Request exactly at MAX_REQUESTS boundary is still allowed")
    void requestAtBoundaryIsAllowed() {
        // 100 requests should all pass (the 100th is the last allowed)
        for (int i = 0; i < 99; i++) {
            rateLimiterService.isAllowed("boundary-client");
        }
        assertThat(rateLimiterService.isAllowed("boundary-client").allowed()).isTrue();
    }

    @Test
    @DisplayName("Request 101 is rejected when limit is 100")
    void requestOverLimitIsRejected() {
        for (int i = 0; i < 100; i++) {
            rateLimiterService.isAllowed("over-limit-client");
        }
        RateLimitDecision rejected = rateLimiterService.isAllowed("over-limit-client");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
    }

    @Test
    @DisplayName("Different clients have independent counters")
    void clientsAreIsolated() {
        for (int i = 0; i < 100; i++) {
            rateLimiterService.isAllowed("heavy-client");
        }
        // heavy-client is exhausted but other-client should still be fine
        assertThat(rateLimiterService.isAllowed("other-client").allowed()).isTrue();
    }

    // ── AbuseDetectionService ─────────────────────────────────────────────────

    @Test
    @DisplayName("Client is not blocked before any violations")
    void notBlockedInitially() {
        assertThat(abuseDetectionService.isBlocked("clean-client")).isFalse();
    }

    @Test
    @DisplayName("Client is blocked after 3 violations (Tier 1)")
    void blockedAfterThreeViolations() {
        String id = "abuser-tier1";
        abuseDetectionService.recordViolation(id);
        abuseDetectionService.recordViolation(id);
        assertThat(abuseDetectionService.isBlocked(id)).isFalse();
        abuseDetectionService.recordViolation(id);
        assertThat(abuseDetectionService.isBlocked(id)).isTrue();
    }

    @Test
    @DisplayName("Admin unblock clears block and violation counter")
    void unblockClearsState() {
        String id = "abuser-unblock";
        abuseDetectionService.recordViolation(id);
        abuseDetectionService.recordViolation(id);
        abuseDetectionService.recordViolation(id);
        assertThat(abuseDetectionService.isBlocked(id)).isTrue();

        abuseDetectionService.unblock(id);
        assertThat(abuseDetectionService.isBlocked(id)).isFalse();
    }

    @Test
    @DisplayName("recordViolation returns the updated count")
    void violationCountIncrementsCorrectly() {
        String id = "count-client";
        assertThat(abuseDetectionService.recordViolation(id)).isEqualTo(1L);
        assertThat(abuseDetectionService.recordViolation(id)).isEqualTo(2L);
        assertThat(abuseDetectionService.recordViolation(id)).isEqualTo(3L);
    }
}
