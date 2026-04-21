package com.sentinel.ratelimiter.dto;

/**
 * Service Contract / API Response DTO
 *
 * Returned by every rate-limiting endpoint. All microservices that integrate
 * with this service should parse this object to decide whether to proceed.
 *
 * Fields:
 *   allowed        – true if the request is permitted to continue
 *   remainingTokens – tokens left in the client's bucket after this request
 *   message        – human-readable status (useful for debugging integrations)
 */
public class RateLimitResponse {

    private boolean allowed;
    private long remainingTokens;
    private String message;

    public RateLimitResponse(boolean allowed, long remainingTokens, String message) {
        this.allowed         = allowed;
        this.remainingTokens = remainingTokens;
        this.message         = message;
    }

    // ---- Getters (required for JSON serialisation) ----

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public String getMessage() {
        return message;
    }
}