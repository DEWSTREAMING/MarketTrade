package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigParser;

class MarketConfigTest {
    @Test
    void defaultFileGivesDefaults() {
        MarketConfigParser.Result result = MarketConfigParser.parse(MarketConfigParser.DEFAULT_JSON);
        assertEquals(MarketConfig.defaults(), result.config());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void goodValuesAreUsed() {
        MarketConfigParser.Result result = MarketConfigParser
                .parse("{\"cutPercent\": 10, \"listingDays\": 3, \"maxListingsPerPlayer\": 50}");
        assertEquals(new MarketConfig(10, 3, 50), result.config());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void boundariesAreAllowed() {
        assertEquals(0, MarketConfigParser.parse("{\"cutPercent\": 0}").config().cutPercent());
        assertEquals(100, MarketConfigParser.parse("{\"cutPercent\": 100}").config().cutPercent());
        assertEquals(1, MarketConfigParser.parse("{\"listingDays\": 1}").config().listingDays());
        assertEquals(1, MarketConfigParser.parse("{\"maxListingsPerPlayer\": 1}").config().maxListingsPerPlayer());
    }

    @Test
    void emptyObjectUsesDefaultsQuietly() {
        MarketConfigParser.Result result = MarketConfigParser.parse("{}");
        assertEquals(MarketConfig.defaults(), result.config());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void eachBadValueFallsBackAlone() {
        MarketConfigParser.Result cut = MarketConfigParser
                .parse("{\"cutPercent\": 101, \"listingDays\": 3, \"maxListingsPerPlayer\": 8}");
        assertEquals(new MarketConfig(5, 3, 8), cut.config());
        assertEquals(1, cut.errors().size());

        assertEquals(5, MarketConfigParser.parse("{\"cutPercent\": -1}").config().cutPercent());
        assertEquals(5, MarketConfigParser.parse("{\"cutPercent\": 2.5}").config().cutPercent());
        assertEquals(5, MarketConfigParser.parse("{\"cutPercent\": \"ten\"}").config().cutPercent());
        assertEquals(7, MarketConfigParser.parse("{\"listingDays\": 0}").config().listingDays());
        assertEquals(7, MarketConfigParser.parse("{\"listingDays\": 1.5}").config().listingDays());
        assertEquals(7, MarketConfigParser.parse("{\"listingDays\": true}").config().listingDays());
        assertEquals(20, MarketConfigParser.parse("{\"maxListingsPerPlayer\": 0}").config().maxListingsPerPlayer());
        assertEquals(20, MarketConfigParser.parse("{\"maxListingsPerPlayer\": []}").config().maxListingsPerPlayer());
        assertEquals(1, MarketConfigParser.parse("{\"cutPercent\": 200}").errors().size());
    }

    @Test
    void wholeNumberWrittenWithAPointZeroIsFine() {
        assertEquals(12, MarketConfigParser.parse("{\"cutPercent\": 12.0}").config().cutPercent());
    }

    @Test
    void hugeListingDaysIsAllowedAndAbsurdOnesAreRefused() {
        MarketConfigParser.Result big = MarketConfigParser.parse("{\"listingDays\": 9000000000000000000}");
        assertEquals(9_000_000_000_000_000_000L, big.config().listingDays());
        MarketConfigParser.Result tooBig = MarketConfigParser.parse("{\"listingDays\": 99999999999999999999999}");
        assertEquals(7, tooBig.config().listingDays());
        assertEquals(1, tooBig.errors().size());
        assertEquals(20, MarketConfigParser.parse("{\"maxListingsPerPlayer\": 99999999999}").config()
                .maxListingsPerPlayer());
    }

    @Test
    void unparseableFileUsesAllDefaults() {
        MarketConfigParser.Result broken = MarketConfigParser.parse("{ nope");
        assertEquals(MarketConfig.defaults(), broken.config());
        assertEquals(1, broken.errors().size());
        MarketConfigParser.Result notObject = MarketConfigParser.parse("[1, 2]");
        assertEquals(MarketConfig.defaults(), notObject.config());
        assertEquals(1, notObject.errors().size());
        assertEquals(MarketConfig.defaults(), MarketConfigParser.parse("").config());
    }
}
