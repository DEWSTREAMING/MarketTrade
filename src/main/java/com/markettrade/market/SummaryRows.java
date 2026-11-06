package com.markettrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Groups listings by item for Browse, and orders the groups. No Minecraft classes. */
public final class SummaryRows {
    public enum Sort {
        CHEAPEST, MOST_LISTED, NAME;

        public Sort next() {
            Sort[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the grouping needs to know about one listing. */
    public record Entry(String itemId, long price, boolean available) {
    }

    private SummaryRows() {
    }

    public static List<SummaryRow> group(List<Entry> entries, LongFunction<String> priceText) {
        Map<String, long[]> byItem = new LinkedHashMap<>();
        Map<String, Boolean> availability = new LinkedHashMap<>();
        for (Entry entry : entries) {
            long[] acc = byItem.computeIfAbsent(entry.itemId(), k -> new long[] { 0, Long.MAX_VALUE });
            acc[0]++;
            acc[1] = Math.min(acc[1], entry.price());
            availability.merge(entry.itemId(), entry.available(), (a, b) -> a && b);
        }
        List<SummaryRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byItem.entrySet()) {
            long lowest = e.getValue()[1];
            rows.add(new SummaryRow(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()[0]), lowest,
                    priceText.apply(lowest), availability.get(e.getKey())));
        }
        return rows;
    }

    /** A new list. Ties fall back to the name and then the id, so the order never flickers. */
    public static List<SummaryRow> sorted(List<SummaryRow> rows, Sort mode, Function<String, String> nameOf) {
        Comparator<SummaryRow> byName = Comparator
                .comparing((SummaryRow r) -> nameOf.apply(r.itemId()).toLowerCase(Locale.ROOT))
                .thenComparing(SummaryRow::itemId);
        Comparator<SummaryRow> order = switch (mode) {
            case CHEAPEST -> Comparator.comparingLong(SummaryRow::lowest).thenComparing(byName);
            case MOST_LISTED -> Comparator.comparingInt(SummaryRow::listed).reversed().thenComparing(byName);
            case NAME -> byName;
        };
        List<SummaryRow> copy = new ArrayList<>(rows);
        copy.sort(order);
        return copy;
    }
}
