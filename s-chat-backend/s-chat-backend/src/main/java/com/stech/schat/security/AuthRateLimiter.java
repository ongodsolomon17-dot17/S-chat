package com.stech.schat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps a token bucket per client IP so a single source can't hammer
 * /api/auth/login or /api/auth/signup. This is a first line of defense;
 * the per-account lockout in AuthService is the second.
 */
@Component
public class AuthRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        // 10 attempts per minute per IP, refilling gradually
        Bandwidth limit = Bandwidth.classic(10, io.github.bucket4j.Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
