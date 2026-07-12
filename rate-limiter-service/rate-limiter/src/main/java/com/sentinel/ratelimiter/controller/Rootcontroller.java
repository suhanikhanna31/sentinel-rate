package com.sentinel.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the bare root path ("/"), which otherwise has no mapping and falls
 * through to Spring Boot's default Whitelabel 404 page. This just gives
 * visitors (and health-check bots) something informative instead.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "sentinel-rate");
        body.put("status", "ok");
        body.put("message", "Sentinel Rate Limiter is running. See /health for a liveness check.");
        return ResponseEntity.ok(body);
    }
}
