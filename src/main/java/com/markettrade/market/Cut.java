package com.markettrade.market;

/** The market's share of a sale. No Minecraft classes. */
public final class Cut {
    private Cut() {
    }

    /** The cut for a price, rounded down. Split in two parts so a price near Long.MAX_VALUE can't overflow. */
    public static long of(long price, int percent) {
        if (price < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be 0 to 100");
        }
        return price / 100 * percent + price % 100 * percent / 100;
    }
}
