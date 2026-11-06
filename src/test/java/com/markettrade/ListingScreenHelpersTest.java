package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.markettrade.market.AmountParser;
import com.markettrade.market.ListingSort;
import com.markettrade.market.SortMode;
import com.markettrade.market.TimeLeft;

class ListingScreenHelpersTest {
    private record Row(long id, long price, long expires, String name) {
    }

    private static final List<Row> ROWS = List.of(
            new Row(1, 50, 900, "Banana"),
            new Row(2, 10, 300, "apple"),
            new Row(3, 50, 600, "Cherry"),
            new Row(4, 99, 100, "apple pie"));

    private static List<Long> order(SortMode mode) {
        return ListingSort.sorted(ROWS, mode, Row::id, Row::price, Row::expires, Row::name).stream().map(Row::id).toList();
    }

    private static final CurrencyConfig COINS = new CurrencyConfig("coins", List.of(
            new CurrencyItem("a:copper", 1, "copper"),
            new CurrencyItem("a:silver", 8, "silver"),
            new CurrencyItem("a:gold", 64, "gold"),
            new CurrencyItem("a:big", 512, "diamond block")));

    private static AmountParser.Result parse(String text) {
        return AmountParser.parse(text, COINS);
    }

    @Test
    void newestFirstIsHighestIdFirst() {
        assertEquals(List.of(4L, 3L, 2L, 1L), order(SortMode.NEWEST));
    }

    @Test
    void cheapestAndDearestBreakTiesByNewest() {
        assertEquals(List.of(2L, 3L, 1L, 4L), order(SortMode.CHEAPEST));
        assertEquals(List.of(4L, 3L, 1L, 2L), order(SortMode.DEAREST));
    }

    @Test
    void endingSoonPutsTheEarliestExpiryFirst() {
        assertEquals(List.of(4L, 2L, 3L, 1L), order(SortMode.ENDING_SOON));
    }

    @Test
    void nameSortIgnoresCase() {
        assertEquals(List.of(2L, 4L, 1L, 3L), order(SortMode.NAME));
    }

    @Test
    void sortingDoesNotChangeTheInputList() {
        order(SortMode.CHEAPEST);
        assertEquals(1L, ROWS.get(0).id());
    }

    @Test
    void sortModeCyclesThroughAllFiveAndBack() {
        SortMode mode = SortMode.NEWEST;
        for (int i = 0; i < 5; i++) {
            mode = mode.next();
        }
        assertEquals(SortMode.NEWEST, mode);
        assertEquals(SortMode.CHEAPEST, SortMode.NEWEST.next());
        assertEquals(SortMode.NEWEST, SortMode.NAME.next());
    }

    @Test
    void searchMatchesNameOrIdIgnoringCase() {
        assertTrue(ListingSort.matches("Dragon Slayer", "minecraft:diamond_sword", "dRAGON"));
        assertTrue(ListingSort.matches("Dragon Slayer", "minecraft:diamond_sword", "diamond_sw"));
        assertTrue(ListingSort.matches("Dragon Slayer", "minecraft:diamond_sword", "  slayer "));
        assertFalse(ListingSort.matches("Dragon Slayer", "minecraft:diamond_sword", "pickaxe"));
    }

    @Test
    void emptySearchMatchesEverything() {
        assertTrue(ListingSort.matches("Anything", "a:b", ""));
        assertTrue(ListingSort.matches("Anything", "a:b", "   "));
        assertTrue(ListingSort.matches("Anything", "a:b", null));
    }

    @Test
    void timeLeftShowsTheTwoBiggestUnits() {
        long minute = 60_000L;
        long hour = 60 * minute;
        long day = 24 * hour;
        assertEquals("2d 5h", TimeLeft.format(2 * day + 5 * hour + 30 * minute));
        assertEquals("7d 0h", TimeLeft.format(7 * day));
        assertEquals("3h 15m", TimeLeft.format(3 * hour + 15 * minute));
        assertEquals("12m", TimeLeft.format(12 * minute));
        assertEquals("<1m", TimeLeft.format(59_999));
        assertEquals("<1m", TimeLeft.format(0));
    }

    @Test
    void timeLeftNeverGoesNegativeAndSurvivesTheEndOfTime() {
        assertEquals("<1m", TimeLeft.until(100, 5000));
        assertEquals("1h 0m", TimeLeft.until(3_600_000L + 10, 10));
        assertTrue(TimeLeft.until(Long.MAX_VALUE, 0).endsWith("h"));
    }

    @Test
    void aWholeNumberIsTheSmallestUnit() {
        assertEquals(new AmountParser.Result(AmountParser.Status.OK, 250), parse("250"));
        assertEquals(new AmountParser.Result(AmountParser.Status.OK, 250), parse("  250 "));
    }

    @Test
    void theDisplayedFormIsRead() {
        assertEquals(130, parse("2 gold 2 copper").amount());
        assertEquals(3 * 64 + 2 * 8, parse("3 gold 2 silver").amount());
        assertEquals(3 * 64 + 2 * 8, parse("3 GOLD 2 Silver").amount());
        assertEquals(3 * 64 + 2 * 8, parse("3gold2silver").amount());
        assertEquals(1024 + 5, parse("2 diamond block 5 coins").amount());
        assertEquals(64, parse("1 gold").amount());
    }

    @Test
    void theSameUnitTwiceAddsUp() {
        assertEquals(16, parse("1 silver 1 silver").amount());
    }

    @Test
    void badTextIsRefused() {
        assertEquals(AmountParser.Status.BAD, parse("gold").status());
        assertEquals(AmountParser.Status.BAD, parse("3 rubies").status());
        assertEquals(AmountParser.Status.BAD, parse("3 gold silver").status());
        assertEquals(AmountParser.Status.BAD, parse("-5").status());
        assertEquals(AmountParser.Status.BAD, parse("1.5").status());
        assertEquals(AmountParser.Status.BAD, parse("12 34").status());
        assertEquals(AmountParser.Status.BAD, parse("2 gold x").status());
    }

    @Test
    void emptyTextIsEmpty() {
        assertEquals(AmountParser.Status.EMPTY, parse("").status());
        assertEquals(AmountParser.Status.EMPTY, parse("   ").status());
        assertEquals(AmountParser.Status.EMPTY, parse(null).status());
    }

    @Test
    void overflowIsCaughtInEveryWay() {
        assertEquals(AmountParser.Status.TOO_BIG, parse("99999999999999999999").status());
        assertEquals(AmountParser.Status.TOO_BIG, parse("9223372036854775807 gold").status());
        assertEquals(AmountParser.Status.TOO_BIG, parse("9223372036854775807 coins 1 copper").status());
        assertEquals(Long.MAX_VALUE, parse("9223372036854775807").amount());
    }

    @Test
    void zeroParsesAndTheCallerRefusesIt() {
        assertEquals(new AmountParser.Result(AmountParser.Status.OK, 0), parse("0"));
        assertEquals(0, parse("0 gold").amount());
    }

    @Test
    void currencyWithoutItemsStillReadsPlainNumbersAndItsName() {
        CurrencyConfig none = CurrencyConfig.noItems();
        assertEquals(40, AmountParser.parse("40", none).amount());
        assertEquals(40, AmountParser.parse("40 coins", none).amount());
        assertEquals(AmountParser.Status.BAD, AmountParser.parse("40 gold", none).status());
    }
}
