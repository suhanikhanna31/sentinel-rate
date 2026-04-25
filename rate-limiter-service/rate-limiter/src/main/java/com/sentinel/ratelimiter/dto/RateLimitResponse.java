package com.sentinel.ratelimiter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified API response for all rate-limit decisions.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code allowed}         – whether the request was permitted</li>
 *   <li>{@code remainingTokens} – requests remaining in the current window</li>
 *   <li>{@code message}         – optional human-readable reason (null when omitted)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitResponse {

    private final boolean allowed;
    private final long    remainingTokens;
    private final String  message;

    public RateLimitResponse(boolean allowed, long remainingTokens) {
        this(allowed, remainingTokens, null);
    }

    public RateLimitResponse(boolean allowed, long remainingTokens, String message) {
        this.allowed         = allowed;
        this.remainingTokens = remainingTokens;
        this.message         = message;
    }

    public boolean isAllowed()         { return allowed; }
    public long    getRemainingTokens(){ return remainingTokens; }
    public String  getMessage()        { return message; }
}
