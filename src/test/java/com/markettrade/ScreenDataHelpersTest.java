package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.markettrade.market.RateLimiter;
import com.markettrade.market.Sale;
import com.markettrade.market.SalesChart;
import com.markettrade.market.SummaryRow;
import com.markettrade.market.SummaryRows;
import com.markettrade.market.SummaryRows.Entry;

class ScreenDataHelpersTest {
    private static final long DAY = SalesChart.DAY_MILLIS;
    private static final UUID A = UUID.randomUUID();

    private static Sale sale(long time, String item, int count, long price) {
        return new Sale(time, A, "a", A, "b", item, count, price, 0);
    }

    @Test
    void salesChartAveragesPerItemPerDayAndLeavesEmptyDaysEmpty() {
        long now = 100 * DAY + 5_000;
        List<Sale> sales = List.of(
                sale(now, "minecraft:log", 64, 128),
                sale(now - 10, "minecraft:log", 64, 256),
                sale(now - 2 * DAY, "minecraft:log", 1, 7),
                sale(now - 13 * DAY, "minecraft:log", 2, 9),
                sale(now - 14 * DAY, "minecraft:log", 1, 1000),
                sale(now - 3 * DAY, "minecraft:stone", 1, 500));
        SalesChart.Chart chart = SalesChart.build(sales, "minecraft:log", now);
        assertEquals(SalesChart.DAYS, chart.days().length);
        assertEquals(3, chart.days()[13], "today: 384 over 128 items");
        assertEquals(7, chart.days()[11]);
        assertEquals(4, chart.days()[0], "13 days ago, 9 over 2 rounds down");
        assertEquals(SalesChart.EMPTY, chart.days()[12]);
        assertEquals(SalesChart.EMPTY, chart.days()[5]);
        // 14 days ago is out of the window.
        assertEquals((128 + 256 + 7 + 9) / (64 + 64 + 1 + 2), chart.average());
    }

    @Test
    void salesChartWithNoSalesIsAllEmpty() {
        SalesChart.Chart chart = SalesChart.build(List.of(), "minecraft:log", 5 * DAY);
        for (long day : chart.days()) {
            assertEquals(SalesChart.EMPTY, day);
        }
        assertEquals(SalesChart.EMPTY, chart.average());
    }

    @Test
    void salesChartDoesNotOverflow() {
        long now = 50 * DAY;
        SalesChart.Chart chart = SalesChart.build(List.of(sale(now, "x:y", 1, Long.MAX_VALUE), sale(now, "x:y", 1, Long.MAX_VALUE)),
                "x:y", now);
        assertTrue(chart.days()[13] > 0);
    }

    @Test
    void groupingKeepsTheLowestPriceAndTheCount() {
        List<Entry> entries = List.of(new Entry("a:one", 50, true), new Entry("a:two", 5, true), new Entry("a:one", 20, true),
                new Entry("a:one", 90, true), new Entry("b:gone", 7, false));
        List<SummaryRow> rows = SummaryRows.group(entries, p -> p + "c");
        assertEquals(3, rows.size());
        SummaryRow one = rows.stream().filter(r -> r.itemId().equals("a:one")).findFirst().orElseThrow();
        assertEquals(3, one.listed());
        assertEquals(20, one.lowest());
        assertEquals("20c", one.lowestText());
        assertTrue(one.available());
        SummaryRow gone = rows.stream().filter(r -> r.itemId().equals("b:gone")).findFirst().orElseThrow();
        assertFalse(gone.available());
    }

    @Test
    void sortingByPriceCountAndName() {
        List<SummaryRow> rows = List.of(new SummaryRow("m:apple", 3, 30, "", true), new SummaryRow("m:bread", 9, 10, "", true),
                new SummaryRow("m:cake", 9, 10, "", true), new SummaryRow("m:dust", 1, 99, "", true));
        Map<String, String> names = Map.of("m:apple", "Zebra", "m:bread", "Bread", "m:cake", "apple pie", "m:dust", "Dust");
        assertEquals(List.of("m:cake", "m:bread", "m:apple", "m:dust"),
                SummaryRows.sorted(rows, SummaryRows.Sort.CHEAPEST, names::get).stream().map(SummaryRow::itemId).toList());
        assertEquals(List.of("m:cake", "m:bread", "m:apple", "m:dust"),
                SummaryRows.sorted(rows, SummaryRows.Sort.MOST_LISTED, names::get).stream().map(SummaryRow::itemId).toList());
        assertEquals(List.of("m:cake", "m:bread", "m:dust", "m:apple"),
                SummaryRows.sorted(rows, SummaryRows.Sort.NAME, names::get).stream().map(SummaryRow::itemId).toList());
        assertEquals(SummaryRows.Sort.CHEAPEST, SummaryRows.Sort.NAME.next());
    }

    @Test
    void rateLimiterDropsTheExtrasAndRefillsOverTime() {
        RateLimiter limiter = new RateLimiter(20);
        UUID p = UUID.randomUUID();
        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.allow(p, 1_000)) {
                allowed++;
            }
        }
        assertEquals(20, allowed);
        assertFalse(limiter.allow(p, 1_010));
        assertTrue(limiter.allow(p, 1_100), "100 ms later there is room for two more");
        assertTrue(limiter.allow(UUID.randomUUID(), 1_000), "another player has their own bucket");
    }
}
