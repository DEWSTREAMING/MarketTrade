package com.markettrade.market;

/** Short time text for lore, like {@code 2d 5h}. */
public final class TimeLeft {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeLeft() {
    }

    public static String format(long millis) {
        if (millis < MINUTE) {
            return "<1m";
        }
        long days = millis / DAY;
        long hours = (millis % DAY) / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    /** Time from {@code now} to {@code expiresAt}, never negative. */
    public static String until(long expiresAt, long now) {
        if (expiresAt <= now) {
            return format(0);
        }
        return format(expiresAt - now);
    }
}
