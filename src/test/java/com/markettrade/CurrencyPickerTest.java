package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.markettrade.market.CurrencyPicker;
import com.markettrade.market.CurrencyPicker.Pick;
import com.markettrade.market.CurrencyPicker.Source;
import com.markettrade.market.CurrencyPicker.Take;
import com.markettrade.market.Sale;
import com.markettrade.market.SalesLog;

class CurrencyPickerTest {
    @Test
    void smallestValueIsTakenFirst() {
        List<Source> sources = List.of(new Source(0, 9, 3), new Source(1, 1, 5));
        Pick pick = CurrencyPicker.pick(sources, 4);
        assertEquals(List.of(new Take(1, 4)), pick.takes());
        assertEquals(0, pick.change());
    }

    @Test
    void exactAmountNeedsNoChange() {
        List<Source> sources = List.of(new Source(0, 9, 3), new Source(1, 1, 5));
        Pick pick = CurrencyPicker.pick(sources, 5 + 9);
        assertEquals(List.of(new Take(1, 5), new Take(0, 1)), pick.takes());
        assertEquals(0, pick.change());
    }

    @Test
    void changeIsWhatIsOverpaid() {
        List<Source> sources = List.of(new Source(0, 9, 3), new Source(1, 1, 2));
        Pick pick = CurrencyPicker.pick(sources, 5);
        assertEquals(List.of(new Take(1, 2), new Take(0, 1)), pick.takes());
        assertEquals(6, pick.change());
    }

    @Test
    void onlyAsManyAsNeeded() {
        Pick pick = CurrencyPicker.pick(List.of(new Source(3, 10, 64)), 25);
        assertEquals(List.of(new Take(3, 3)), pick.takes());
        assertEquals(5, pick.change());
    }

    @Test
    void notEnoughGivesNull() {
        assertNull(CurrencyPicker.pick(List.of(new Source(0, 9, 1), new Source(1, 1, 3)), 13));
        assertNull(CurrencyPicker.pick(List.of(), 1));
    }

    @Test
    void zeroShortfallTakesNothing() {
        Pick pick = CurrencyPicker.pick(List.of(new Source(0, 9, 1)), 0);
        assertTrue(pick.takes().isEmpty());
        assertEquals(0, pick.change());
    }

    @Test
    void equalValuesGoInSlotOrder() {
        Pick pick = CurrencyPicker.pick(List.of(new Source(7, 1, 2), new Source(2, 1, 2)), 3);
        assertEquals(List.of(new Take(2, 2), new Take(7, 1)), pick.takes());
    }

    @Test
    void hugeValuesDoNotOverflow() {
        long big = Long.MAX_VALUE / 2 + 10;
        Pick pick = CurrencyPicker.pick(List.of(new Source(0, big, 2)), Long.MAX_VALUE - 5);
        assertEquals(List.of(new Take(0, 2)), pick.takes());
        // Two items cover the shortfall by 24, even though 2 * big itself would overflow.
        assertEquals(24, pick.change());
    }

    @Test
    void canPayAddsWalletAndItems() {
        List<Source> sources = List.of(new Source(0, 9, 2));
        assertTrue(CurrencyPicker.canPay(2, sources, 20));
        assertFalse(CurrencyPicker.canPay(1, sources, 20));
        assertTrue(CurrencyPicker.canPay(20, List.of(), 20));
        assertFalse(CurrencyPicker.canPay(19, List.of(), 20));
    }

    @Test
    void overflowingTotalsCountAsAbleToPay() {
        List<Source> sources = List.of(new Source(0, Long.MAX_VALUE, 5));
        assertEquals(Long.MAX_VALUE, CurrencyPicker.total(sources));
        assertTrue(CurrencyPicker.canPay(Long.MAX_VALUE, sources, Long.MAX_VALUE));
        assertTrue(CurrencyPicker.canPay(Long.MAX_VALUE - 1, List.of(new Source(0, 5, 5)), Long.MAX_VALUE));
    }

    @Test
    void salesLogKeepsTheNewestThousandAndCapsTotals() {
        SalesLog log = new SalesLog();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        for (int i = 0; i < 1005; i++) {
            log.record(new Sale(i, seller, "s", buyer, "b", "minecraft:dirt", 1, 1, 0));
        }
        assertEquals(1000, log.recent().size());
        assertEquals(5, log.recent().get(0).time());
        assertEquals(1004, log.recent().get(999).time());
        assertEquals(1005, log.lifetime(seller));

        log.record(new Sale(0, seller, "s", buyer, "b", "minecraft:dirt", 1, Long.MAX_VALUE, 0));
        assertEquals(Long.MAX_VALUE, log.lifetime(seller));
        log.record(new Sale(0, seller, "s", buyer, "b", "minecraft:dirt", 1, 10, 0));
        assertEquals(Long.MAX_VALUE, log.lifetime(seller));
    }
}
