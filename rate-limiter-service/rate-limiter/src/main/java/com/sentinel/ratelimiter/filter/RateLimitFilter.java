package com.sentinel.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.ratelimiter.dto.RateLimitResponse;
import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.RateLimiterService;
import com.sentinel.ratelimiter.service.RateLimiterService.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces rate limiting and abuse detection on every
 * inbound request, short-circuiting the filter chain with a 429 or 403
 * before the request ever reaches a controller.
 *
 * <p>Adds standard rate-limit headers to every allowed response:
 * <ul>
 *   <li>{@code X-RateLimit-Limit}     – max requests per window</li>
 *   <li>{@code X-RateLimit-Remaining} – requests left in the current window</li>
 *   <li>{@code X-RateLimit-Reset}     – Unix epoch seconds when the window resets</li>
 *   <li>{@code Retry-After}           – seconds until next allowed request (429 only)</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int     MAX_REQUESTS  = 100;
    private static final int     WINDOW_SECS   = 60;
    private static final String  HEADER_LIMIT  = "X-RateLimit-Limit";
    private static final String  HEADER_REM    = "X-RateLimit-Remaining";
    private static final String  HEADER_RESET  = "X-RateLimit-Reset";
    private static final String  HEADER_RETRY  = "Retry-After";

    private final RateLimiterService    rateLimiterService;
    private final AbuseDetectionService abuseDetectionService;
    private final ObjectMapper          objectMapper;

    public RateLimitFilter(
            RateLimiterService rateLimiterService,
            AbuseDetectionService abuseDetectionService,
            ObjectMapper objectMapper
    ) {
        this.rateLimiterService    = rateLimiterService;
        this.abuseDetectionService = abuseDetectionService;
        this.objectMapper          = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientId = resolveClientId(request);

        // ── Hard block check ────────────────────────────────────────────────
        if (abuseDetectionService.isBlocked(clientId)) {
            rejectWith(response, HttpStatus.FORBIDDEN,
                    new RateLimitResponse(false, 0, "Client blocked due to repeated abuse"));
            return;
        }

        // ── Sliding-window rate limit ────────────────────────────────────────
        RateLimitDecision decision = rateLimiterService.isAllowed(clientId);
        long resetEpoch = (System.currentTimeMillis() / 1000) + WINDOW_SECS;

        response.setIntHeader(HEADER_LIMIT, MAX_REQUESTS);
        response.setIntHeader(HEADER_REM,   decision.remaining());
        response.setLongHeader(HEADER_RESET, resetEpoch);

        if (!decision.allowed()) {
            abuseDetectionService.recordViolation(clientId);
            response.setIntHeader(HEADER_RETRY, WINDOW_SECS);
            rejectWith(response, HttpStatus.TOO_MANY_REQUESTS,
                    new RateLimitResponse(false, 0, "Rate limit exceeded"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a stable client identifier. Prefers the {@code X-Forwarded-For}
     * header (set by load-balancers / proxies) and falls back to the remote
     * address for direct connections.
     */
    private String resolveClientId(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF may contain a comma-separated chain; take the first (original) IP
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void rejectWith(HttpServletResponse response, HttpStatus status, RateLimitResponse body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
