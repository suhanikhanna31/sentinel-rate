package com.sentinel.ratelimiter.config;

/**
 * Single source of truth for the rate limiter's default request budget.
 *
 * <p>Previously {@code MAX_REQUESTS} and the window size were declared separately in both
 * {@link com.sentinel.ratelimiter.service.RateLimiterService} and
 * {@link com.sentinel.ratelimiter.filter.RateLimitFilter}, which meant changing the limit
 * required remembering to update it in two places. Both now read from here instead.
 *
 * <p>Per-endpoint overrides don't touch these constants at all — they go through the
 * {@code @RateLimit} annotation (see {@link com.sentinel.ratelimiter.aspect.RateLimitAspect}),
 * which calls {@link com.sentinel.ratelimiter.service.RateLimiterService#isAllowed(String, int, int)}
 * directly with its own values.
 */
public final class RateLimitDefaults {

    /** Maximum requests allowed inside the rolling window, absent a per-endpoint override. */
    public static final int DEFAULT_MAX_REQUESTS = 100;

    /** Rolling window size, in seconds, absent a per-endpoint override. */
    public static final int DEFAULT_WINDOW_SECONDS = 60;

    private RateLimitDefaults() {}
}
