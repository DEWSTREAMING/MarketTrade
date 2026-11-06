package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.markettrade.market.Cut;
import com.markettrade.market.Expiry;

class CutTest {
    @Test
    void zeroPercentTakesNothing() {
        assertEquals(0, Cut.of(12345, 0));
    }

    @Test
    void fivePercent() {
        assertEquals(5, Cut.of(100, 5));
        assertEquals(50, Cut.of(1000, 5));
    }

    @Test
    void hundredPercentTakesAll() {
        assertEquals(777, Cut.of(777, 100));
    }

    @Test
    void roundsDown() {
        assertEquals(0, Cut.of(19, 5));
        assertEquals(1, Cut.of(20, 5));
        assertEquals(1, Cut.of(39, 5));
        assertEquals(6, Cut.of(125, 5));
    }

    @Test
    void priceOfOne() {
        assertEquals(0, Cut.of(1, 5));
        assertEquals(1, Cut.of(1, 100));
    }

    @Test
    void maxPriceDoesNotOverflow() {
        for (int percent : new int[] { 0, 1, 5, 50, 99, 100 }) {
            long expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(percent))
                    .divide(BigInteger.valueOf(100)).longValueExact();
            assertEquals(expected, Cut.of(Long.MAX_VALUE, percent), "percent " + percent);
        }
    }

    @Test
    void matchesExactMathOnOddPrices() {
        long[] prices = { 1, 7, 99, 101, 12345, 9_999_999_999L, Long.MAX_VALUE - 1, Long.MAX_VALUE / 3 };
        for (long price : prices) {
            for (int percent = 0; percent <= 100; percent += 7) {
                long expected = BigInteger.valueOf(price).multiply(BigInteger.valueOf(percent))
                        .divide(BigInteger.valueOf(100)).longValueExact();
                assertEquals(expected, Cut.of(price, percent));
            }
        }
    }

    @Test
    void badInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Cut.of(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> Cut.of(10, 101));
        assertThrows(IllegalArgumentException.class, () -> Cut.of(10, -1));
    }

    @Test
    void expiryAddsDays() {
        assertEquals(1000 + 7L * 86_400_000L, Expiry.at(1000, 7));
    }

    @Test
    void expiryStopsAtMaxInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, Expiry.at(1000, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, Expiry.at(1000, Long.MAX_VALUE / 86_400_000L + 1));
        assertEquals(Long.MAX_VALUE, Expiry.at(Long.MAX_VALUE, 1));
    }

    @Test
    void expiredFromTheExactTime() {
        assertFalse(Expiry.isExpired(1000, 999));
        assertTrue(Expiry.isExpired(1000, 1000));
        assertTrue(Expiry.isExpired(1000, 1001));
    }
}
