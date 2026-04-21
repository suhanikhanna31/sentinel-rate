package com.sentinel.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Centralized Redis Configuration
 *
 * This class configures a single, shared Redis connection used by all microservices
 * for rate-limiting state. Centralizing cache access here ensures:
 *   1. Consistent serialisation across services (String keys & values only).
 *   2. A single place to swap connection settings (host, pool, TLS).
 *   3. All rate-limiting policies can reference the bean contract below.
 *
 * ── Rate-Limiting Key Namespace Policy ─────────────────────────────────────
 *   rate_limit:tokens:<clientId>       – remaining tokens in the client's bucket
 *   rate_limit:last_refill:<clientId>  – epoch-ms of the last token refill
 *   abuse:count:<clientId>             – violation count (rolling 5-min window)
 *   abuse:block:<clientId>             – present when client is hard-blocked
 *   abuse:offence_count:<clientId>     – lifetime block count (for adaptive TTL)
 *
 * ── TTL / Expiry Policy ─────────────────────────────────────────────────────
 *   Bucket keys expire after 10 min of inactivity (set in RateLimiterService).
 *   Abuse-block keys expire per the adaptive schedule (set in AbuseDetectionService).
 *   No key is set without a TTL – prevents unbounded memory growth.
 *
 * ── Integration Contract for Downstream Microservices ───────────────────────
 *   Services should call POST /check?clientId=<id> to validate a request.
 *   The response (RateLimitResponse) includes:
 *     • allowed         – whether the request may proceed
 *     • remainingTokens – tokens left in the bucket (0 when blocked)
 *   HTTP 429 means rate-limited; HTTP 403 means the client is hard-blocked.
 */
@Configuration
public class RedisConfig {

    /**
     * Provides a StringRedisTemplate configured with String serialisers on both
     * key and value channels. All rate-limiting services must inject this bean
     * rather than creating their own templates, to guarantee a single connection pool.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}