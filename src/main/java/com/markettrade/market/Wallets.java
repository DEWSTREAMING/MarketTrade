package com.markettrade.market;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who has how much, in the smallest unit. No Minecraft classes. A balance is never negative. */
public final class Wallets {
    private final Map<UUID, Long> balances = new HashMap<>();

    public long balance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    /** Fails and changes nothing if the amount is negative or the balance would overflow. */
    public boolean add(UUID player, long amount) {
        if (amount < 0) {
            return false;
        }
        long current = balance(player);
        if (amount > Long.MAX_VALUE - current) {
            return false;
        }
        store(player, current + amount);
        return true;
    }

    /** Fails and changes nothing if the amount is negative or more than the balance. */
    public boolean take(UUID player, long amount) {
        if (amount < 0) {
            return false;
        }
        long current = balance(player);
        if (amount > current) {
            return false;
        }
        store(player, current - amount);
        return true;
    }

    /** Fails and changes nothing if the amount is negative. */
    public boolean set(UUID player, long amount) {
        if (amount < 0) {
            return false;
        }
        store(player, amount);
        return true;
    }

    public boolean canAdd(UUID player, long amount) {
        return amount >= 0 && amount <= Long.MAX_VALUE - balance(player);
    }

    public Map<UUID, Long> entries() {
        return Collections.unmodifiableMap(balances);
    }

    private void store(UUID player, long amount) {
        if (amount == 0) {
            balances.remove(player);
        } else {
            balances.put(player, amount);
        }
    }
}
