package com.sentinel.ratelimiter;

import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.RateLimiterService;
import com.sentinel.ratelimiter.service.RateLimiterService.RateLimitDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Filter-layer tests using MockMvc with mocked Redis services.
 * These verify HTTP response codes and header injection without
 * needing a live Redis instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RateLimiterService    rateLimiterService;
    @MockBean private AbuseDetectionService abuseDetectionService;

    @Test
    @DisplayName("Allowed request returns 200 with rate-limit headers")
    void allowedRequestReturns200WithHeaders() throws Exception {
        when(abuseDetectionService.isBlocked(anyString())).thenReturn(false);
        when(rateLimiterService.isAllowed(anyString())).thenReturn(new RateLimitDecision(true, 99));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().string("X-RateLimit-Remaining", "99"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("Exceeded rate limit returns 429 with Retry-After header")
    void exceededLimitReturns429() throws Exception {
        when(abuseDetectionService.isBlocked(anyString())).thenReturn(false);
        when(rateLimiterService.isAllowed(anyString())).thenReturn(new RateLimitDecision(false, 0));

        mockMvc.perform(get("/health"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    @DisplayName("Blocked client receives 403 Forbidden")
    void blockedClientReceives403() throws Exception {
        when(abuseDetectionService.isBlocked(anyString())).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    @DisplayName("X-Forwarded-For header is used for client identification")
    void xForwardedForIsRespected() throws Exception {
        when(abuseDetectionService.isBlocked("10.0.0.1")).thenReturn(false);
        when(rateLimiterService.isAllowed("10.0.0.1")).thenReturn(new RateLimitDecision(true, 95));

        mockMvc.perform(get("/health").header("X-Forwarded-For", "10.0.0.1, 192.168.1.1"))
                .andExpect(status().isOk());
    }
}
