package com.markettrade.market;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The newest sales, plus what each seller has earned in total. No Minecraft classes. */
public final class SalesLog {
    public static final int KEEP = 1000;

    private final Deque<Sale> recent = new ArrayDeque<>();
    private final Map<UUID, Long> lifetime = new HashMap<>();

    /** Oldest first. */
    public List<Sale> recent() {
        return List.copyOf(recent);
    }

    public Map<UUID, Long> lifetimeTotals() {
        return Collections.unmodifiableMap(lifetime);
    }

    public long lifetime(UUID seller) {
        return lifetime.getOrDefault(seller, 0L);
    }

    /** Keeps the record and adds the price to the seller's total. */
    public void record(Sale sale) {
        addRecord(sale);
        addLifetime(sale.seller(), sale.price());
    }

    /** Used when loading, where the totals are loaded on their own. */
    public void addRecord(Sale sale) {
        recent.addLast(sale);
        while (recent.size() > KEEP) {
            recent.removeFirst();
        }
    }

    public void addLifetime(UUID seller, long amount) {
        if (amount < 0) {
            return;
        }
        long current = lifetime.getOrDefault(seller, 0L);
        lifetime.put(seller, amount > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + amount);
    }
}
