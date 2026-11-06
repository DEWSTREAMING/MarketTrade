package com.markettrade.market;

/** Listing times in real time (epoch millis). No Minecraft classes. */
public final class Expiry {
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    private Expiry() {
    }

    /** When a listing made at {@code now} runs out. Stops at Long.MAX_VALUE instead of overflowing. */
    public static long at(long now, long days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must not be negative");
        }
        try {
            return Math.addExact(now, Math.multiplyExact(days, MILLIS_PER_DAY));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public static boolean isExpired(long expiresAt, long now) {
        return now >= expiresAt;
    }
}
