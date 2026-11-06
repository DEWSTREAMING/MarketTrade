package com.markettrade.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Allows a number of requests per second for each player and drops the rest. A bucket that refills over time, so a burst up to
 * the limit is fine. No Minecraft classes, and time is passed in.
 */
public final class RateLimiter {
    private static final class Bucket {
        double tokens;
        long last;
    }

    private final double perSecond;
    private final double burst;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    public RateLimiter(int perSecond) {
        if (perSecond < 1) {
            throw new IllegalArgumentException("perSecond must be at least 1");
        }
        this.perSecond = perSecond;
        this.burst = perSecond;
    }

    /** True if this request may go ahead. */
    public synchronized boolean allow(UUID player, long nowMillis) {
        Bucket bucket = buckets.get(player);
        if (bucket == null) {
            bucket = new Bucket();
            bucket.tokens = burst;
            bucket.last = nowMillis;
            buckets.put(player, bucket);
        }
        long elapsed = Math.max(0, nowMillis - bucket.last);
        bucket.last = nowMillis;
        bucket.tokens = Math.min(burst, bucket.tokens + elapsed * perSecond / 1000.0);
        if (bucket.tokens < 1) {
            return false;
        }
        bucket.tokens -= 1;
        return true;
    }

    public synchronized void forget(UUID player) {
        buckets.remove(player);
    }

    public synchronized void clear() {
        buckets.clear();
    }
}
