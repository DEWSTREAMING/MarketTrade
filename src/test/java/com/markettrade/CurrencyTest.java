package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.markettrade.currency.CurrencyParser;

class CurrencyTest {
    private static final Set<String> ALL_HD = Set.of("holeydigsite:copper_coin", "holeydigsite:silver_coin",
            "holeydigsite:gold_coin", "holeydigsite:platinum_coin", "holeydigsite:ruby_crown", "holeydigsite:mint_bar");

    private static CurrencyConfig holey() throws Exception {
        String json = Files.readString(Path.of("docs/examples/currency-holeydigsite.json"));
        CurrencyParser.Result result = CurrencyParser.parse(json, ALL_HD::contains);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        return result.config();
    }

    private static CurrencyConfig diamonds() {
        return CurrencyParser.defaultConfig();
    }

    @Test
    void defaultConfigIsDiamonds() {
        CurrencyConfig config = diamonds();
        assertEquals("diamonds", config.name());
        assertEquals(2, config.items().size());
        assertEquals("minecraft:diamond_block", config.items().get(0).item());
        assertEquals(9, config.items().get(0).value());
    }

    @Test
    void holeyDigsiteConfigLoadsBiggestFirst() throws Exception {
        CurrencyConfig config = holey();
        assertEquals("coins", config.name());
        assertEquals(6, config.items().size());
        assertEquals(32768, config.items().get(0).value());
        assertEquals("copper", config.items().get(5).name());
    }

    @Test
    void emptyListIsFineAndNonPhysical() {
        CurrencyParser.Result result = CurrencyParser.parse("{\"name\":\"gems\",\"items\":[]}", id -> true);
        assertTrue(result.errors().isEmpty());
        assertFalse(result.config().hasItems());
        assertEquals("gems", result.config().name());
        assertEquals("1234 gems", result.config().format(1234));
        assertEquals("0 gems", result.config().format(0));
    }

    @Test
    void unknownItemIsSkippedOnly() {
        CurrencyParser.Result result = CurrencyParser.parse(
                "{\"name\":\"x\",\"items\":[{\"item\":\"a:b\",\"value\":1,\"name\":\"b\"},"
                        + "{\"item\":\"c:d\",\"value\":5,\"name\":\"d\"}]}",
                id -> id.equals("c:d"));
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("a:b"));
        assertEquals(1, result.config().items().size());
        assertEquals("c:d", result.config().items().get(0).item());
    }

    @Test
    void zeroNegativeAndFractionValuesAreSkipped() {
        String json = "{\"name\":\"x\",\"items\":["
                + "{\"item\":\"a:zero\",\"value\":0,\"name\":\"z\"},"
                + "{\"item\":\"a:neg\",\"value\":-3,\"name\":\"n\"},"
                + "{\"item\":\"a:frac\",\"value\":1.5,\"name\":\"f\"},"
                + "{\"item\":\"a:text\",\"value\":\"4\",\"name\":\"t\"},"
                + "{\"item\":\"a:ok\",\"value\":2,\"name\":\"ok\"}]}";
        CurrencyParser.Result result = CurrencyParser.parse(json, id -> true);
        assertEquals(4, result.errors().size());
        assertEquals(1, result.config().items().size());
    }

    @Test
    void itemListedTwiceKeepsFirst() {
        String json = "{\"name\":\"x\",\"items\":["
                + "{\"item\":\"a:b\",\"value\":1,\"name\":\"first\"},"
                + "{\"item\":\"a:b\",\"value\":7,\"name\":\"second\"}]}";
        CurrencyParser.Result result = CurrencyParser.parse(json, id -> true);
        assertEquals(1, result.errors().size());
        assertEquals(1, result.config().items().size());
        assertEquals("first", result.config().items().get(0).name());
    }

    @Test
    void twoItemsWithOneValueKeepsFirst() {
        String json = "{\"name\":\"x\",\"items\":["
                + "{\"item\":\"a:one\",\"value\":4,\"name\":\"one\"},"
                + "{\"item\":\"a:two\",\"value\":4,\"name\":\"two\"},"
                + "{\"item\":\"a:three\",\"value\":8,\"name\":\"three\"}]}";
        CurrencyParser.Result result = CurrencyParser.parse(json, id -> true);
        assertEquals(1, result.errors().size());
        assertEquals(2, result.config().items().size());
        assertEquals("one", result.config().items().get(1).name());
    }

    @Test
    void unparseableFileGivesNoItemsUnderCoins() {
        for (String bad : List.of("{ not json", "[]", "", "{\"name\":\"x\"}")) {
            CurrencyParser.Result result = CurrencyParser.parse(bad, id -> true);
            assertFalse(result.errors().isEmpty(), bad);
            assertFalse(result.config().hasItems(), bad);
            assertEquals("coins", result.config().name(), bad);
        }
    }

    @Test
    void nonEmptyListWithNothingValidGivesNoItemsUnderCoins() {
        CurrencyParser.Result result = CurrencyParser.parse(
                "{\"name\":\"gems\",\"items\":[{\"item\":\"a:b\",\"value\":0,\"name\":\"b\"}]}", id -> true);
        assertFalse(result.config().hasItems());
        assertEquals("coins", result.config().name());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void splitExactAmounts() throws Exception {
        CurrencyConfig config = holey();
        CurrencyConfig.Split split = config.split(3 * 64 + 2 * 8);
        assertEquals(0, split.remainder());
        // biggest first: bar, crown, platinum, gold, silver, copper
        assertEquals(List.of(0L, 0L, 0L, 3L, 2L, 0L), toList(split.counts()));
    }

    @Test
    void splitKeepsRemainders() throws Exception {
        CurrencyConfig config = holey();
        CurrencyConfig.Split split = config.split(32768 * 2 + 4096 + 512 + 64 + 8 + 1);
        assertEquals(List.of(2L, 1L, 1L, 1L, 1L, 1L), toList(split.counts()));
        assertEquals(0, split.remainder());
    }

    @Test
    void splitWithValuesThatDoNotDivideEachOther() {
        CurrencyConfig config = new CurrencyConfig("x", List.of(
                new CurrencyItem("a:a", 7, "a"), new CurrencyItem("a:b", 3, "b")));
        CurrencyConfig.Split split = config.split(20);
        assertEquals(List.of(2L, 2L), toList(split.counts()));
        assertEquals(0, split.remainder());
        CurrencyConfig.Split odd = config.split(2);
        assertEquals(List.of(0L, 0L), toList(odd.counts()));
        assertEquals(2, odd.remainder());
    }

    @Test
    void splitWithNoValueOneItemLeavesRemainder() {
        CurrencyConfig config = new CurrencyConfig("x", List.of(new CurrencyItem("a:a", 8, "eight")));
        CurrencyConfig.Split split = config.split(21);
        assertEquals(List.of(2L), toList(split.counts()));
        assertEquals(5, split.remainder());
    }

    @Test
    void splitZeroAndMax() throws Exception {
        CurrencyConfig config = holey();
        CurrencyConfig.Split zero = config.split(0);
        assertEquals(0, zero.remainder());
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L), toList(zero.counts()));

        CurrencyConfig.Split max = config.split(Long.MAX_VALUE);
        long total = 0;
        for (int i = 0; i < max.counts().length; i++) {
            total = Math.addExact(total, Math.multiplyExact(max.counts()[i], config.items().get(i).value()));
        }
        assertEquals(Long.MAX_VALUE, Math.addExact(total, max.remainder()));
        assertEquals(Long.MAX_VALUE / 32768, max.counts()[0]);
    }

    @Test
    void formatWithTotalAddsThePlainAmountWhenItSaysSomethingNew() throws Exception {
        CurrencyConfig config = holey();
        assertEquals("1 platinum 7 gold 5 silver (1000 copper)", config.formatWithTotal(1000));
        assertEquals("2 silver (16 copper)", config.formatWithTotal(16));
        assertEquals("5 copper", config.formatWithTotal(5));
        assertEquals("0 copper", config.formatWithTotal(0));
        assertEquals("1 diamond block 2 diamond (11 diamond)", diamonds().formatWithTotal(11));
    }

    @Test
    void formatWithTotalWithoutAValueOneItemUsesTheCurrencyName() {
        CurrencyConfig config = new CurrencyConfig("coins", List.of(new CurrencyItem("a:b", 8, "silver")));
        assertEquals("1 silver 2 coins (10 coins)", config.formatWithTotal(10));
        assertEquals("5 coins", config.formatWithTotal(5));
        assertEquals("12 coins", new CurrencyConfig("coins", List.of()).formatWithTotal(12));
    }

    @Test
    void formatBiggestFirstAndSkipsZeroes() throws Exception {
        CurrencyConfig config = holey();
        assertEquals("3 gold 2 silver", config.format(3 * 64 + 2 * 8));
        assertEquals("1 bar 5 copper", config.format(32768 + 5));
        assertEquals("1 crown", config.format(4096));
    }

    @Test
    void formatZeroUsesSmallestItemName() throws Exception {
        assertEquals("0 copper", holey().format(0));
        assertEquals("0 diamond", diamonds().format(0));
    }

    @Test
    void formatRemainderUsesCurrencyName() {
        CurrencyConfig config = new CurrencyConfig("coins", List.of(new CurrencyItem("a:a", 8, "silver")));
        assertEquals("2 silver 5 coins", config.format(21));
        assertEquals("5 coins", config.format(5));
    }

    @Test
    void formatMaxDoesNotOverflow() throws Exception {
        String text = holey().format(Long.MAX_VALUE);
        assertTrue(text.startsWith(Long.MAX_VALUE / 32768 + " bar"), text);
    }

    private static List<Long> toList(long[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
