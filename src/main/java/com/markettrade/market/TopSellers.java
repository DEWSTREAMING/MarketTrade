package com.markettrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Ranks sellers by what they have earned in total. No Minecraft classes. */
public final class TopSellers {
    public record Row(UUID seller, String name, long total) {
    }

    private TopSellers() {
    }

    /**
     * Highest total first. Equal totals are ordered by name, then id, so the order never changes between calls. A name comes
     * from the newest sale of that seller, else from {@code profiles} (may return null), else it is the id.
     */
    public static List<Row> rank(Map<UUID, Long> lifetime, List<Sale> sales, Function<UUID, String> profiles) {
        Map<UUID, Sale> newest = new HashMap<>();
        for (Sale sale : sales) {
            Sale seen = newest.get(sale.seller());
            if (seen == null || sale.time() >= seen.time()) {
                newest.put(sale.seller(), sale);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : lifetime.entrySet()) {
            UUID id = entry.getKey();
            String name = null;
            Sale sale = newest.get(id);
            if (sale != null && sale.sellerName() != null && !sale.sellerName().isBlank()) {
                name = sale.sellerName();
            }
            if (name == null && profiles != null) {
                String found = profiles.apply(id);
                if (found != null && !found.isBlank()) {
                    name = found;
                }
            }
            rows.add(new Row(id, name != null ? name : id.toString(), entry.getValue()));
        }
        rows.sort(Comparator.comparingLong(Row::total).reversed()
                .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Row::seller));
        return rows;
    }

    /** Sum of the cuts of sales at or after {@code since}. Only the sales still kept are counted. */
    public static long cutSince(List<Sale> sales, long since) {
        long total = 0;
        for (Sale sale : sales) {
            if (sale.time() >= since && sale.cut() > 0) {
                total = sale.cut() > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + sale.cut();
            }
        }
        return total;
    }
}
