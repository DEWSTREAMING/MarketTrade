package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.markettrade.market.WalletBackup;
import com.markettrade.market.Wallets;

class WalletsTest {
    private final UUID alice = UUID.randomUUID();

    @Test
    void unknownPlayerHasZero() {
        assertEquals(0, new Wallets().balance(alice));
    }

    @Test
    void addAndTake() {
        Wallets wallets = new Wallets();
        assertTrue(wallets.add(alice, 100));
        assertTrue(wallets.take(alice, 40));
        assertEquals(60, wallets.balance(alice));
        assertTrue(wallets.set(alice, 5));
        assertEquals(5, wallets.balance(alice));
    }

    @Test
    void takingTooMuchChangesNothing() {
        Wallets wallets = new Wallets();
        wallets.add(alice, 10);
        assertFalse(wallets.take(alice, 11));
        assertEquals(10, wallets.balance(alice));
        assertFalse(wallets.take(UUID.randomUUID(), 1));
    }

    @Test
    void overflowChangesNothing() {
        Wallets wallets = new Wallets();
        wallets.add(alice, Long.MAX_VALUE - 5);
        assertFalse(wallets.add(alice, 6));
        assertEquals(Long.MAX_VALUE - 5, wallets.balance(alice));
        assertTrue(wallets.add(alice, 5));
        assertEquals(Long.MAX_VALUE, wallets.balance(alice));
        assertFalse(wallets.add(alice, 1));
        assertFalse(wallets.canAdd(alice, 1));
    }

    @Test
    void negativeAmountsAreRefused() {
        Wallets wallets = new Wallets();
        wallets.add(alice, 10);
        assertFalse(wallets.add(alice, -1));
        assertFalse(wallets.take(alice, -1));
        assertFalse(wallets.set(alice, -1));
        assertFalse(wallets.add(alice, Long.MIN_VALUE));
        assertEquals(10, wallets.balance(alice));
    }

    @Test
    void backupCopiesNextToTheFileAndKeepsTheOriginal(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("markettrade_wallets.dat");
        Files.writeString(file, "data");
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        Path first = WalletBackup.copyAside(file, now);
        Path second = WalletBackup.copyAside(file, now);
        assertEquals(dir, first.getParent());
        assertNotEquals(first, second);
        assertEquals("data", Files.readString(first));
        assertEquals("data", Files.readString(file));
        assertTrue(first.getFileName().toString().contains("20260102-030405"));
    }
}
