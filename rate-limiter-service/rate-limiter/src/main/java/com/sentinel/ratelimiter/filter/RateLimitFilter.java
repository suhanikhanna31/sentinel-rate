package com.sentinel.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.ratelimiter.dto.RateLimitResponse;
import com.sentinel.ratelimiter.service.AbuseDetectionService;
import com.sentinel.ratelimiter.service.EventPersistenceService;
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

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int    MAX_REQUESTS = 100;
    private static final int    WINDOW_SECS  = 60;
    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REM   = "X-RateLimit-Remaining";
    private static final String HEADER_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RETRY = "Retry-After";

    private final RateLimiterService      rateLimiterService;
    private final AbuseDetectionService   abuseDetectionService;
    private final EventPersistenceService persistenceService;
    private final ObjectMapper            objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiterService,
                           AbuseDetectionService abuseDetectionService,
                           EventPersistenceService persistenceService,
                           ObjectMapper objectMapper) {
        this.rateLimiterService    = rateLimiterService;
        this.abuseDetectionService = abuseDetectionService;
        this.persistenceService    = persistenceService;
        this.objectMapper          = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = resolveClientId(request);

        if (abuseDetectionService.isBlocked(clientId)) {
            persistenceService.saveRateLimitEvent(clientId, false, 0);
            rejectWith(response, HttpStatus.FORBIDDEN,
                    new RateLimitResponse(false, 0, "Client blocked due to repeated abuse"));
            return;
        }

        RateLimitDecision decision   = rateLimiterService.isAllowed(clientId);
        long              resetEpoch = (System.currentTimeMillis() / 1000) + WINDOW_SECS;

        response.setIntHeader(HEADER_LIMIT, MAX_REQUESTS);
        response.setIntHeader(HEADER_REM,   decision.remaining());
        response.setLongHeader(HEADER_RESET, resetEpoch);

        persistenceService.saveRateLimitEvent(clientId, decision.allowed(), decision.remaining());

        if (!decision.allowed()) {
            abuseDetectionService.recordViolation(clientId);
            response.setIntHeader(HEADER_RETRY, WINDOW_SECS);
            rejectWith(response, HttpStatus.TOO_MANY_REQUESTS,
                    new RateLimitResponse(false, 0, "Rate limit exceeded"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private void rejectWith(HttpServletResponse response, HttpStatus status, RateLimitResponse body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}