package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;

import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Everything that moves money in and out of wallets. Commands and screens call these and turn the result into text.
 * Each action does every check that can fail before anything moves.
 */
public final class WalletActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAIN_SLOTS = 36;
    private static final int OFFHAND_SLOT = 40;

    public enum DepositStatus {
        OK, UNAVAILABLE, NOT_CURRENCY, ONLY_CUSTOM, EMPTY, OVERFLOW
    }

    /** {@code skippedCustom} is true when currency items with data were left with the player. */
    public record DepositResult(DepositStatus status, long deposited, long balance, boolean skippedCustom) {
    }

    public enum WithdrawStatus {
        OK, PARTIAL, UNAVAILABLE, INSUFFICIENT, NOTHING_COVERED
    }

    /** {@code given} is the value of the items handed over. What was asked for and not given stays in the wallet. */
    public record WithdrawResult(WithdrawStatus status, long requested, long given, long balance) {
    }

    public enum AdminStatus {
        OK, OVERFLOW, INSUFFICIENT, INVALID
    }

    public record AdminResult(AdminStatus status, long balance) {
    }

    private WalletActions() {
    }

    public static long balance(MinecraftServer server, UUID player) {
        return WalletData.get(server).balance(player);
    }

    public static DepositResult depositHand(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        return deposit(player, List.of(inventory.selected));
    }

    public static DepositResult depositAll(ServerPlayer player) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            slots.add(i);
        }
        slots.add(OFFHAND_SLOT);
        return deposit(player, slots);
    }

    private static DepositResult deposit(ServerPlayer player, List<Integer> slots) {
        CurrencyConfig currency = Currencies.current();
        WalletData data = WalletData.get(player.server);
        long balance = data.balance(player.getUUID());
        if (!currency.hasItems()) {
            return new DepositResult(DepositStatus.UNAVAILABLE, 0, balance, false);
        }

        Inventory inventory = player.getInventory();
        List<Integer> taking = new ArrayList<>();
        long total = 0;
        boolean skippedCustom = false;
        try {
            for (int slot : slots) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                CurrencyItem entry = currency.find(idOf(stack.getItem()));
                if (entry == null) {
                    continue;
                }
                if (!stack.isComponentsPatchEmpty()) {
                    skippedCustom = true;
                    continue;
                }
                total = Math.addExact(total, CurrencyConfig.valueOf(entry, stack.getCount()));
                taking.add(slot);
            }
        } catch (ArithmeticException e) {
            return new DepositResult(DepositStatus.OVERFLOW, 0, balance, skippedCustom);
        }

        if (taking.isEmpty()) {
            DepositStatus status = skippedCustom ? DepositStatus.ONLY_CUSTOM
                    : slots.size() == 1 ? DepositStatus.NOT_CURRENCY : DepositStatus.EMPTY;
            return new DepositResult(status, 0, balance, skippedCustom);
        }
        if (!data.canAdd(player.getUUID(), total)) {
            return new DepositResult(DepositStatus.OVERFLOW, 0, balance, skippedCustom);
        }

        for (int slot : taking) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        data.add(player.getUUID(), total);
        return new DepositResult(DepositStatus.OK, total, data.balance(player.getUUID()), skippedCustom);
    }

    public static WithdrawResult withdrawAll(ServerPlayer player) {
        long balance = WalletData.get(player.server).balance(player.getUUID());
        return withdraw(player, balance);
    }

    public static WithdrawResult withdraw(ServerPlayer player, long amount) {
        CurrencyConfig currency = Currencies.current();
        WalletData data = WalletData.get(player.server);
        UUID id = player.getUUID();
        long balance = data.balance(id);
        if (!currency.hasItems()) {
            return new WithdrawResult(WithdrawStatus.UNAVAILABLE, amount, 0, balance);
        }
        if (amount < 0 || amount > balance || balance == 0) {
            return new WithdrawResult(WithdrawStatus.INSUFFICIENT, amount, 0, balance);
        }

        CurrencyConfig.Split split = currency.split(amount);
        long withdrawable = amount - split.remainder();
        if (withdrawable == 0) {
            return new WithdrawResult(WithdrawStatus.NOTHING_COVERED, amount, 0, balance);
        }

        data.take(id, withdrawable);

        Inventory inventory = player.getInventory();
        long givenValue = 0;
        boolean blocked = false;
        for (int i = 0; i < split.counts().length && !blocked; i++) {
            long count = split.counts()[i];
            if (count == 0) {
                continue;
            }
            CurrencyItem entry = currency.items().get(i);
            Item item = itemOf(entry.item());
            if (item == null) {
                blocked = true;
                break;
            }
            int maxStack = new ItemStack(item).getMaxStackSize();
            long remaining = count;
            while (remaining > 0) {
                int size = (int) Math.min(remaining, maxStack);
                int given = Giving.give(inventory, new ItemStack(item, size));
                remaining -= given;
                givenValue += given * entry.value();
                if (given < size) {
                    blocked = true;
                    break;
                }
            }
        }

        long refund = withdrawable - givenValue;
        if (refund > 0) {
            data.add(id, refund);
        }
        WithdrawStatus status = givenValue == amount ? WithdrawStatus.OK : WithdrawStatus.PARTIAL;
        return new WithdrawResult(status, amount, givenValue, data.balance(id));
    }

    /**
     * Takes {@code count} of one currency item out of the wallet. Unlike {@link #withdraw}, it never swaps small coins for a
     * bigger one. What can't be handed over goes back to the wallet.
     */
    public static WithdrawResult withdrawItem(ServerPlayer player, String itemId, long count) {
        CurrencyConfig currency = Currencies.current();
        WalletData data = WalletData.get(player.server);
        UUID id = player.getUUID();
        long balance = data.balance(id);
        CurrencyItem entry = currency.find(itemId);
        Item item = itemOf(itemId);
        if (entry == null || item == null) {
            return new WithdrawResult(WithdrawStatus.UNAVAILABLE, 0, 0, balance);
        }
        long value;
        try {
            value = CurrencyConfig.valueOf(entry, count);
        } catch (ArithmeticException e) {
            return new WithdrawResult(WithdrawStatus.INSUFFICIENT, 0, 0, balance);
        }
        if (count < 1 || value > balance) {
            return new WithdrawResult(WithdrawStatus.INSUFFICIENT, value, 0, balance);
        }

        data.take(id, value);
        Inventory inventory = player.getInventory();
        int maxStack = new ItemStack(item).getMaxStackSize();
        long remaining = count;
        long givenValue = 0;
        while (remaining > 0) {
            int size = (int) Math.min(remaining, maxStack);
            int given = Giving.give(inventory, new ItemStack(item, size));
            remaining -= given;
            givenValue += given * entry.value();
            if (given < size) {
                break;
            }
        }
        long refund = value - givenValue;
        if (refund > 0) {
            data.add(id, refund);
        }
        WithdrawStatus status = givenValue == value ? WithdrawStatus.OK : WithdrawStatus.PARTIAL;
        return new WithdrawResult(status, value, givenValue, data.balance(id));
    }

    /** Takes a full stack of one currency item, or as many as the wallet covers if that is fewer. */
    public static WithdrawResult withdrawStack(ServerPlayer player, String itemId) {
        CurrencyItem entry = Currencies.current().find(itemId);
        Item item = itemOf(itemId);
        long balance = WalletData.get(player.server).balance(player.getUUID());
        if (entry == null || item == null) {
            return new WithdrawResult(WithdrawStatus.UNAVAILABLE, 0, 0, balance);
        }
        long affordable = balance / entry.value();
        long count = Math.min(new ItemStack(item).getMaxStackSize(), affordable);
        return withdrawItem(player, itemId, count);
    }

    public static AdminResult adminGive(MinecraftServer server, String actor, UUID target, String targetName, long amount) {
        return adminGive(server, Actor.named(actor), target, targetName, amount);
    }

    public static AdminResult adminGive(MinecraftServer server, Actor actor, UUID target, String targetName, long amount) {
        WalletData data = WalletData.get(server);
        if (amount < 0) {
            return new AdminResult(AdminStatus.INVALID, data.balance(target));
        }
        if (!data.add(target, amount)) {
            return new AdminResult(AdminStatus.OVERFLOW, data.balance(target));
        }
        logAdmin(server, "give", actor, target, targetName, amount, data.balance(target));
        return new AdminResult(AdminStatus.OK, data.balance(target));
    }

    public static AdminResult adminTake(MinecraftServer server, String actor, UUID target, String targetName, long amount) {
        return adminTake(server, Actor.named(actor), target, targetName, amount);
    }

    public static AdminResult adminTake(MinecraftServer server, Actor actor, UUID target, String targetName, long amount) {
        WalletData data = WalletData.get(server);
        if (amount < 0) {
            return new AdminResult(AdminStatus.INVALID, data.balance(target));
        }
        if (!data.take(target, amount)) {
            return new AdminResult(AdminStatus.INSUFFICIENT, data.balance(target));
        }
        logAdmin(server, "take", actor, target, targetName, amount, data.balance(target));
        return new AdminResult(AdminStatus.OK, data.balance(target));
    }

    public static AdminResult adminSet(MinecraftServer server, String actor, UUID target, String targetName, long amount) {
        return adminSet(server, Actor.named(actor), target, targetName, amount);
    }

    public static AdminResult adminSet(MinecraftServer server, Actor actor, UUID target, String targetName, long amount) {
        WalletData data = WalletData.get(server);
        if (!data.set(target, amount)) {
            return new AdminResult(AdminStatus.INVALID, data.balance(target));
        }
        logAdmin(server, "set", actor, target, targetName, amount, data.balance(target));
        return new AdminResult(AdminStatus.OK, data.balance(target));
    }

    private static void logAdmin(MinecraftServer server, String action, Actor actor, UUID target, String targetName,
            long amount, long newBalance) {
        AuditLog.log(server, new AuditEntry(System.currentTimeMillis(), "ADMIN_WALLET").actor(actor)
                .target(target, targetName).price(amount).reason(action + ", balance now " + newBalance));
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Item itemOf(String id) {
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }
}
