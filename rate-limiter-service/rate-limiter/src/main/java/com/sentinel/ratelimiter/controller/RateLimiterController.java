package com.sentinel.ratelimiter.controller;

import com.sentinel.ratelimiter.dto.RateLimitResponse;
import com.sentinel.ratelimiter.service.AbuseDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Thin REST controller. Heavy lifting (rate-limit enforcement, header injection,
 * abuse recording) is handled upstream in {@link com.sentinel.ratelimiter.filter.RateLimitFilter}.
 */
@RestController
public class RateLimiterController {

    private final AbuseDetectionService abuseDetectionService;

    public RateLimiterController(AbuseDetectionService abuseDetectionService) {
        this.abuseDetectionService = abuseDetectionService;
    }

    /**
     * Primary health / rate-limit probe endpoint.
     * If this method is reached, the filter already confirmed the request is allowed.
     */
    @GetMapping("/health")
    public ResponseEntity<RateLimitResponse> healthCheck() {
        return ResponseEntity.ok(new RateLimitResponse(true, -1, "OK"));
    }

    /**
     * Admin endpoint to manually unblock a client.
     * In production, guard this with an API-key or role-based security.
     */
    @DeleteMapping("/admin/block/{clientId}")
    public ResponseEntity<String> unblock(@PathVariable String clientId) {
        abuseDetectionService.unblock(clientId);
        return ResponseEntity.ok("Client " + clientId + " unblocked.");
    }
}
