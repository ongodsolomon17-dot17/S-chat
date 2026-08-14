package com.stech.schat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a token bucket per client IP so a single source can't hammer
 * /api/auth/login or /api/auth/signup. This is a first line of defense;
 * the per-account lockout in AuthService is the second.
 *
 * Every distinct IP that ever hits an auth endpoint used to get an entry
 * that lived forever — on a long-running instance that's an unbounded,
 * ever-growing map (a slow memory leak that shows up as rising heap usage
 * and GC pressure over days/weeks of uptime). Entries are now timestamped
 * and swept out once they've been idle past the rate-limit window, so
 * memory use stays bounded by "distinct IPs active recently" instead of
 * "distinct IPs ever seen".
 */
@Component
public class AuthRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration IDLE_EVICTION_AFTER = Duration.ofMinutes(10);

    private static final class TrackedBucket {
        final Bucket bucket;
        volatile Instant lastAccess;

        TrackedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = Instant.now();
        }
    }

    private final ConcurrentMap<String, TrackedBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp) {
        TrackedBucket tracked = buckets.computeIfAbsent(clientIp, ip -> new TrackedBucket(newBucket()));
        tracked.lastAccess = Instant.now();
        return tracked.bucket.tryConsume(1);
    }

    /** Runs every 5 minutes, dropping buckets that have been idle long enough to be irrelevant. */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    void evictStaleBuckets() {
        Instant cutoff = Instant.now().minus(IDLE_EVICTION_AFTER);
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccess.isBefore(cutoff));
    }

    private Bucket newBucket() {
        // 10 attempts per minute per IP, refilling gradually
        Bandwidth limit = Bandwidth.classic(10, io.github.bucket4j.Refill.greedy(10, WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
