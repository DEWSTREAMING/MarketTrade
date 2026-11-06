package com.markettrade.market;

import java.util.List;

/** Average price per item for each of the last days, from the sales log. A day with no sales is left empty. No Minecraft classes. */
public final class SalesChart {
    public static final int DAYS = 14;
    public static final long DAY_MILLIS = 86_400_000L;
    public static final long EMPTY = -1;

    /** {@code days} holds the average per item for each day, oldest first, or {@link #EMPTY}. {@code average} is -1 with no sales. */
    public record Chart(long[] days, long average) {
    }

    private SalesChart() {
    }

    /** Days are UTC days, and the last one is the day of {@code now}. */
    public static Chart build(List<Sale> sales, String itemId, long now) {
        long today = Math.floorDiv(now, DAY_MILLIS);
        long[] price = new long[DAYS];
        long[] items = new long[DAYS];
        boolean[] any = new boolean[DAYS];
        long totalPrice = 0;
        long totalItems = 0;
        for (Sale sale : sales) {
            if (!sale.itemId().equals(itemId) || sale.count() < 1) {
                continue;
            }
            long age = today - Math.floorDiv(sale.time(), DAY_MILLIS);
            if (age < 0 || age >= DAYS) {
                continue;
            }
            int slot = DAYS - 1 - (int) age;
            price[slot] = saturatedAdd(price[slot], sale.price());
            items[slot] += sale.count();
            any[slot] = true;
            totalPrice = saturatedAdd(totalPrice, sale.price());
            totalItems += sale.count();
        }
        long[] out = new long[DAYS];
        for (int i = 0; i < DAYS; i++) {
            out[i] = any[i] ? price[i] / items[i] : EMPTY;
        }
        return new Chart(out, totalItems > 0 ? totalPrice / totalItems : EMPTY);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
