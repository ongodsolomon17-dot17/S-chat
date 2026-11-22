package com.stech.schat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-IP limiter for unauthenticated profile lookups (/api/public/users/{username}) so the
 * endpoint cannot be used to enumerate usernames at speed. Idle entries are evicted.
 */
@Component
public class PublicLookupRateLimiter {

    private static final class Tracked {
        final Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1))))
                .build();
        volatile Instant lastAccess = Instant.now();
    }

    private final ConcurrentMap<String, Tracked> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp) {
        Tracked t = buckets.computeIfAbsent(clientIp, ip -> new Tracked());
        t.lastAccess = Instant.now();
        return t.bucket.tryConsume(1);
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    void evictStale() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
    }
}
