package com.sentinel.ratelimiter.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int requests() default 100;
    int windowSeconds() default 60;
    String keyPrefix() default "";
}
