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

/** Per-user limit on AI requests (protects the Gemini quota/cost): 12 per minute. */
@Component
public class AiRateLimiter {

    private static final class Tracked {
        final Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(12, Refill.greedy(12, Duration.ofMinutes(1))))
                .build();
        volatile Instant lastAccess = Instant.now();
    }

    private final ConcurrentMap<UUID, Tracked> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(UUID userId) {
        Tracked t = buckets.computeIfAbsent(userId, id -> new Tracked());
        t.lastAccess = Instant.now();
        return t.bucket.tryConsume(1);
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    void evictStale() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
    }
}
