package com.sentinel.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMetrics {

    private final Counter allowedRequests;
    private final Counter blockedRequests;

    public RateLimitMetrics(MeterRegistry registry) {
        this.allowedRequests = Counter.builder("sentinel.requests.allowed")
                .description("Total requests allowed through rate limiter")
                .register(registry);
        this.blockedRequests = Counter.builder("sentinel.requests.blocked")
                .description("Total requests blocked by rate limiter")
                .register(registry);
    }

    public void recordAllowed() { allowedRequests.increment(); }
    public void recordBlocked() { blockedRequests.increment(); }
}
