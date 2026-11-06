package com.markettrade.currency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CurrencyConfig {
    public static final String FALLBACK_NAME = "coins";

    /** Result of splitting an amount: counts line up with {@link #items()}, biggest value first. */
    public record Split(long[] counts, long remainder) {
    }

    private final String name;
    private final List<CurrencyItem> items;

    public CurrencyConfig(String name, List<CurrencyItem> items) {
        this.name = name;
        List<CurrencyItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(CurrencyItem::value).reversed());
        this.items = List.copyOf(sorted);
    }

    /** No currency items. Deposit and withdraw are unavailable, wallets still work. */
    public static CurrencyConfig noItems() {
        return new CurrencyConfig(FALLBACK_NAME, List.of());
    }

    public String name() {
        return name;
    }

    /** Biggest value first. */
    public List<CurrencyItem> items() {
        return items;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public CurrencyItem find(String itemId) {
        for (CurrencyItem entry : items) {
            if (entry.item().equals(itemId)) {
                return entry;
            }
        }
        return null;
    }

    public Split split(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        long[] counts = new long[items.size()];
        long left = amount;
        for (int i = 0; i < counts.length; i++) {
            long value = items.get(i).value();
            counts[i] = left / value;
            left = left % value;
        }
        return new Split(counts, left);
    }

    public String format(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (amount == 0) {
            return items.isEmpty() ? "0 " + name : "0 " + items.get(items.size() - 1).name();
        }
        Split split = split(amount);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < split.counts().length; i++) {
            if (split.counts()[i] > 0) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(split.counts()[i]).append(' ').append(items.get(i).name());
            }
        }
        if (split.remainder() > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(split.remainder()).append(' ').append(name);
        }
        return out.toString();
    }

    /** The display form plus the plain total in brackets, like {@code 3 gold 2 silver (208 copper)}. No brackets if they'd say the same. */
    public String formatWithTotal(long amount) {
        String shown = format(amount);
        String total = plainTotal(amount);
        return shown.equals(total) ? shown : shown + " (" + total + ")";
    }

    /** The whole amount in the smallest unit, like {@code 208 copper}. */
    public String plainTotal(long amount) {
        return amount + " " + smallestUnitName();
    }

    private String smallestUnitName() {
        if (!items.isEmpty() && items.get(items.size() - 1).value() == 1) {
            return items.get(items.size() - 1).name();
        }
        return name;
    }

    /** Total value of a count of one item. Throws ArithmeticException on overflow. */
    public static long valueOf(CurrencyItem item, long count) {
        return Math.multiplyExact(item.value(), count);
    }
}
