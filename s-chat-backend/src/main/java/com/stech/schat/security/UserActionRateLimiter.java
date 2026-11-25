package com.stech.schat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** Per-user, per-action limiter (uploads, friend requests...) so one account can't flood storage or other users. */
@Component
public class UserActionRateLimiter {

    private record Key(String action, UUID userId) {}

    private static final class Tracked {
        final Bucket bucket;
        volatile Instant lastAccess = Instant.now();
        Tracked(int perMinute) {
            this.bucket = Bucket.builder()
                    .addLimit(Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1))))
                    .build();
        }
    }

    private final ConcurrentMap<Key, Tracked> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String action, UUID userId, int perMinute) {
        Tracked t = buckets.computeIfAbsent(new Key(action, userId), k -> new Tracked(perMinute));
        t.lastAccess = Instant.now();
        return t.bucket.tryConsume(1);
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    void evictStale() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
    }
}
