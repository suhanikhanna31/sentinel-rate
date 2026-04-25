package com.sentinel.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration.
 *
 * <p>Uses Lettuce (the Spring default) which gives us:
 * <ul>
 *   <li>A single, thread-safe connection shared across all threads (no pool overhead)</li>
 *   <li>Non-blocking I/O via Netty — compatible with Spring WebFlux if added later</li>
 * </ul>
 *
 * <p>Connection parameters come from {@code application.properties}:
 * <pre>
 *   spring.data.redis.host=localhost
 *   spring.data.redis.port=6379
 *   spring.data.redis.timeout=2000ms
 * </pre>
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
