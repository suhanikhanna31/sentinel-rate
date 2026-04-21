package com.sentinel.ratelimiter.controller;

import com.sentinel.ratelimiter.dto.RateLimitResponse;
import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimiterController {

    private final RateLimiterService    rateLimiterService;
    private final AbuseDetectionService abuseDetectionService;

    public RateLimiterController(RateLimiterService rateLimiterService,
                                 AbuseDetectionService abuseDetectionService) {
        this.rateLimiterService    = rateLimiterService;
        this.abuseDetectionService = abuseDetectionService;
    }

    /** Simple liveness probe — no rate limiting. */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Sentinel Rate Limiter is running");
    }

    /**
     * Core rate-check endpoint.
     * Returns 200 if allowed, 429 if rate limited, 403 if hard-blocked.
     */
    @GetMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            HttpServletRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {

        // 1. Resolve client identity
        String clientId = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();

        // 2. Hard-block check
        if (abuseDetectionService.isBlocked(clientId)) {
            return buildResponse(HttpStatus.FORBIDDEN, false, 0,
                    "Client is blocked due to repeated abuse. Try again later.");
        }

        // 3. Token bucket check — returns remaining tokens, or -1 if denied
        long remaining = rateLimiterService.isAllowed(clientId);

        if (remaining < 0) {
            abuseDetectionService.recordViolation(clientId);
            return buildResponse(HttpStatus.TOO_MANY_REQUESTS, false, 0,
                    "Rate limit exceeded. Slow down your requests.");
        }

        // 4. Allowed
        return buildResponse(HttpStatus.OK, true, remaining, "Request allowed.");
    }

    private ResponseEntity<RateLimitResponse> buildResponse(
            HttpStatus status, boolean allowed, long remaining, String message) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit",     String.valueOf(RateLimiterService.MAX_TOKENS));
        headers.set("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(new RateLimitResponse(allowed, remaining, message));
    }
}