package com.sentinel.ratelimiter.controller;

import com.sentinel.ratelimiter.dto.RateLimitResponse;
import com.sentinel.ratelimiter.entity.AbuseViolationRecord;
import com.sentinel.ratelimiter.entity.RateLimitEvent;
import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.EventPersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RateLimiterController {

    private final AbuseDetectionService   abuseDetectionService;
    private final EventPersistenceService persistenceService;

    public RateLimiterController(AbuseDetectionService abuseDetectionService,
                                 EventPersistenceService persistenceService) {
        this.abuseDetectionService = abuseDetectionService;
        this.persistenceService    = persistenceService;
    }

    @GetMapping("/health")
    public ResponseEntity<RateLimitResponse> healthCheck() {
        return ResponseEntity.ok(new RateLimitResponse(true, -1, "OK"));
    }

    @DeleteMapping("/admin/block/{clientId}")
    public ResponseEntity<String> unblock(@PathVariable String clientId) {
        abuseDetectionService.unblock(clientId);
        return ResponseEntity.ok("Client " + clientId + " unblocked.");
    }

    @GetMapping("/admin/audit/events/{clientId}")
    public ResponseEntity<List<RateLimitEvent>> getEvents(@PathVariable String clientId) {
        return ResponseEntity.ok(persistenceService.getEventsForClient(clientId));
    }

    @GetMapping("/admin/audit/violations/{clientId}")
    public ResponseEntity<List<AbuseViolationRecord>> getViolations(@PathVariable String clientId) {
        return ResponseEntity.ok(persistenceService.getViolationsForClient(clientId));
    }

    @GetMapping("/admin/audit/rejected-last-minute/{clientId}")
    public ResponseEntity<Long> rejectedLastMinute(@PathVariable String clientId) {
        return ResponseEntity.ok(persistenceService.countRejectedLast60s(clientId));
    }
}