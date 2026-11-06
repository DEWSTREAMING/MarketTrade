package com.markettrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Works out which currency items cover a shortfall. No Minecraft classes. */
public final class CurrencyPicker {
    private CurrencyPicker() {
    }

    /** A stack of currency items the buyer holds. {@code value} is the value of one item. */
    public record Source(int slot, long value, int count) {
    }

    public record Take(int slot, int count) {
    }

    /** What to take, and the change that goes back to the wallet. */
    public record Pick(List<Take> takes, long change) {
    }

    /** Total value of the sources, stopping at Long.MAX_VALUE. */
    public static long total(List<Source> sources) {
        long total = 0;
        for (Source source : sources) {
            long value;
            try {
                value = Math.multiplyExact(source.value(), (long) source.count());
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
            if (value > Long.MAX_VALUE - total) {
                return Long.MAX_VALUE;
            }
            total += value;
        }
        return total;
    }

    /** True if wallet plus items reach the price. A sum that overflows counts as enough. */
    public static boolean canPay(long wallet, List<Source> sources, long price) {
        long sum = total(sources);
        if (wallet > Long.MAX_VALUE - sum) {
            return true;
        }
        return wallet + sum >= price;
    }

    /**
     * Takes the smallest items first, and only as many as needed. Returns null if the sources can't cover the shortfall.
     * Items of equal value are taken in slot order.
     */
    public static Pick pick(List<Source> sources, long shortfall) {
        if (shortfall < 0) {
            throw new IllegalArgumentException("shortfall must not be negative");
        }
        List<Source> ordered = new ArrayList<>(sources);
        ordered.sort(Comparator.comparingLong(Source::value).thenComparingInt(Source::slot));

        List<Take> takes = new ArrayList<>();
        long remaining = shortfall;
        long change = 0;
        for (Source source : ordered) {
            if (remaining == 0) {
                break;
            }
            if (source.value() <= 0 || source.count() <= 0) {
                continue;
            }
            long needed = remaining / source.value() + (remaining % source.value() > 0 ? 1 : 0);
            if (needed <= source.count()) {
                takes.add(new Take(source.slot(), (int) needed));
                // Overshoot is below one item's value, so this can't overflow.
                change = (source.value() - remaining % source.value()) % source.value();
                remaining = 0;
            } else {
                takes.add(new Take(source.slot(), source.count()));
                remaining -= source.value() * source.count();
            }
        }
        if (remaining > 0) {
            return null;
        }
        return new Pick(takes, change);
    }
}
