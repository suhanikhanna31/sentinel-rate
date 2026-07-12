package com.sentinel.ratelimiter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards every /admin/** endpoint behind a shared-secret API key.
 *
 * <p>This service has no Spring Security dependency on the classpath, so
 * without this filter the /admin/** endpoints — which can unblock any client
 * and read another client's audit history — are reachable by anyone who finds
 * the URL. This filter closes that gap without pulling in a full security
 * framework.
 *
 * <p>Configure the key via the {@code ADMIN_API_KEY} environment variable
 * (mapped to {@code admin.api.key} in application.properties), then send it on
 * every admin request as a header:
 *
 * <pre>
 *   X-Admin-Api-Key: &lt;value&gt;
 * </pre>
 *
 * <p>If {@code ADMIN_API_KEY} is not set, this filter fails closed: every
 * /admin/** request is rejected with 503 rather than silently allowing
 * access with no key configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Admin-Api-Key";
    private static final String ADMIN_PATH_PREFIX = "/admin/";

    private final String configuredApiKey;

    public AdminAuthFilter(@Value("${admin.api.key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin endpoints are disabled: ADMIN_API_KEY is not configured.");
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);

        if (providedKey == null || !constantTimeEquals(providedKey, configuredApiKey)) {
            reject(response, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid " + HEADER_NAME + " header.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }

    /** Constant-time comparison so response timing can't leak how much of the key matched. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
