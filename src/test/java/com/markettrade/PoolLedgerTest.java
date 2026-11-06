package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.markettrade.market.PoolEntry;
import com.markettrade.market.PoolLedger;
import com.markettrade.market.Sale;
import com.markettrade.market.TopSellers;

class PoolLedgerTest {
    private static Sale sale(UUID seller, String name, long time) {
        return new Sale(time, seller, name, UUID.randomUUID(), "Buyer", "minecraft:stick", 1, 100, 5);
    }

    @Test
    void ledgerKeepsTheNewest1000() {
        PoolLedger ledger = new PoolLedger();
        for (int i = 0; i < PoolLedger.KEEP + 25; i++) {
            ledger.add(new PoolEntry(i, PoolEntry.Kind.ADJUST, i, i, null, "Console", null, "", ""));
        }
        assertEquals(PoolLedger.KEEP, ledger.size());
        assertEquals(PoolLedger.KEEP + 24, ledger.newestFirst().get(0).time());
        assertEquals(25, ledger.oldestFirst().get(0).time());
    }

    @Test
    void topSellersAreRankedHighestFirst() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<TopSellers.Row> rows = TopSellers.rank(Map.of(a, 50L, b, 500L, c, 5L), List.of(), id -> null);
        assertEquals(List.of(b, a, c), rows.stream().map(TopSellers.Row::seller).toList());
    }

    @Test
    void tiesAreOrderedByNameThenId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Sale> sales = List.of(sale(a, "Zed", 1), sale(b, "amy", 1));
        List<TopSellers.Row> rows = TopSellers.rank(Map.of(a, 10L, b, 10L), sales, id -> null);
        assertEquals("amy", rows.get(0).name());
        assertEquals("Zed", rows.get(1).name());
    }

    @Test
    void namesComeFromTheNewestSaleThenTheProfileCacheThenTheId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<Sale> sales = List.of(sale(a, "OldName", 1), sale(a, "NewName", 9), sale(a, "Middle", 5));
        List<TopSellers.Row> rows = TopSellers.rank(Map.of(a, 30L, b, 20L, c, 10L), sales,
                id -> id.equals(b) ? "Cached" : null);
        assertEquals("NewName", rows.get(0).name());
        assertEquals("Cached", rows.get(1).name());
        assertEquals(c.toString(), rows.get(2).name());
    }

    @Test
    void cutSinceCountsOnlyRecentSales() {
        UUID a = UUID.randomUUID();
        List<Sale> sales = List.of(sale(a, "A", 10), sale(a, "A", 100), sale(a, "A", 200));
        assertEquals(10, TopSellers.cutSince(sales, 100));
        assertEquals(5, TopSellers.cutSince(sales, 101));
        assertEquals(15, TopSellers.cutSince(sales, 10));
        assertEquals(0, TopSellers.cutSince(sales, 1000));
    }
}
