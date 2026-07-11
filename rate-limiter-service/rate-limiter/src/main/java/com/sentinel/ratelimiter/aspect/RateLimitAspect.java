package com.sentinel.ratelimiter.aspect;

import com.sentinel.ratelimiter.annotation.RateLimit;
import com.sentinel.ratelimiter.service.RateLimiterService;
import com.sentinel.ratelimiter.service.RateLimiterService.RateLimitDecision;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    public RateLimitAspect(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String clientIp = request.getRemoteAddr();
        String key = rateLimit.keyPrefix().isEmpty()
            ? clientIp
            : rateLimit.keyPrefix() + ":" + clientIp;

        RateLimitDecision decision =
                rateLimiterService.isAllowed(key, rateLimit.requests(), rateLimit.windowSeconds());
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
        return joinPoint.proceed();
    }
}
