package com.markettrade.devtest;

import java.util.UUID;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.market.WalletActions;
import com.markettrade.market.WalletData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase2Tests {

    private static ServerPlayer setUp(GameTestHelper helper) {
        Currencies.set(CurrencyParser.defaultConfig());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        return player;
    }

    private static long balance(ServerPlayer player) {
        return WalletData.get(player.server).balance(player.getUUID());
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int droppedItems(GameTestHelper helper) {
        return helper.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> true).size();
    }

    @GameTest(template = "empty")
    public static void depositHandAndAllGiveRightBalanceAndRemoveRightItems(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();

        inv.setItem(inv.selected, new ItemStack(Items.DIAMOND_BLOCK, 3));
        WalletActions.DepositResult hand = WalletActions.depositHand(player);
        helper.assertTrue(hand.status() == WalletActions.DepositStatus.OK, "Hand deposit should work");
        helper.assertTrue(balance(player) == 27, "3 blocks are 27, got " + balance(player));
        helper.assertTrue(inv.getItem(inv.selected).isEmpty(), "Hand should be empty after deposit");

        inv.setItem(10, new ItemStack(Items.DIAMOND_BLOCK, 2));
        inv.setItem(11, new ItemStack(Items.DIAMOND, 5));
        inv.setItem(12, new ItemStack(Items.DIRT, 7));
        inv.setItem(40, new ItemStack(Items.DIAMOND, 1));
        WalletActions.DepositResult all = WalletActions.depositAll(player);
        helper.assertTrue(all.status() == WalletActions.DepositStatus.OK, "Deposit all should work");
        helper.assertTrue(all.deposited() == 24, "Deposited 24, got " + all.deposited());
        helper.assertTrue(balance(player) == 51, "Balance should be 51, got " + balance(player));
        helper.assertTrue(inv.getItem(10).isEmpty() && inv.getItem(11).isEmpty() && inv.getItem(40).isEmpty(),
                "Currency slots should be empty");
        helper.assertTrue(inv.getItem(12).is(Items.DIRT) && inv.getItem(12).getCount() == 7, "Dirt must stay");

        ItemStack notCurrency = new ItemStack(Items.DIRT);
        inv.setItem(inv.selected, notCurrency);
        helper.assertTrue(WalletActions.depositHand(player).status() == WalletActions.DepositStatus.NOT_CURRENCY,
                "Dirt is not currency");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renamedCurrencyItemIsNotDepositedAndKeepsItsName(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        ItemStack named = new ItemStack(Items.DIAMOND, 4);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky"));
        inv.setItem(10, named);
        inv.setItem(inv.selected, named.copy());

        helper.assertTrue(WalletActions.depositAll(player).status() == WalletActions.DepositStatus.ONLY_CUSTOM,
                "Only renamed items, so nothing deposited");
        helper.assertTrue(WalletActions.depositHand(player).status() == WalletActions.DepositStatus.ONLY_CUSTOM,
                "Hand is renamed too");
        helper.assertTrue(balance(player) == 0, "Balance must stay 0");
        ItemStack after = inv.getItem(10);
        helper.assertTrue(after.getCount() == 4 && after.get(DataComponents.CUSTOM_NAME) != null
                && after.get(DataComponents.CUSTOM_NAME).getString().equals("Lucky"), "Name and count must stay");

        inv.setItem(11, new ItemStack(Items.DIAMOND, 2));
        WalletActions.DepositResult mixed = WalletActions.depositAll(player);
        helper.assertTrue(mixed.status() == WalletActions.DepositStatus.OK && mixed.skippedCustom(),
                "Plain diamonds go in, the report says some were kept");
        helper.assertTrue(balance(player) == 2, "Only the plain 2 count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void depositThatWouldOverflowChangesNothing(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        WalletData.get(player.server).set(player.getUUID(), Long.MAX_VALUE - 5);
        inv.setItem(10, new ItemStack(Items.DIAMOND, 6));

        WalletActions.DepositResult result = WalletActions.depositAll(player);
        helper.assertTrue(result.status() == WalletActions.DepositStatus.OVERFLOW, "Should refuse");
        helper.assertTrue(balance(player) == Long.MAX_VALUE - 5, "Balance unchanged");
        helper.assertTrue(inv.getItem(10).getCount() == 6, "Items unchanged");
        WalletData.get(player.server).set(player.getUUID(), 0);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withdrawPaysBiggestFirst(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        WalletData.get(player.server).set(player.getUUID(), 20);

        WalletActions.WithdrawResult result = WalletActions.withdraw(player, 20);
        helper.assertTrue(result.status() == WalletActions.WithdrawStatus.OK, "Should be fine");
        helper.assertTrue(count(inv, Items.DIAMOND_BLOCK) == 2, "Two blocks");
        helper.assertTrue(count(inv, Items.DIAMOND) == 2, "Two diamonds");
        helper.assertTrue(balance(player) == 0, "Wallet empty");

        helper.assertTrue(WalletActions.withdraw(player, 5).status() == WalletActions.WithdrawStatus.INSUFFICIENT,
                "Too much is refused");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withdrawIntoOffhandStackIsCountedAsGiven(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        inv.setItem(40, new ItemStack(Items.DIAMOND, 1));
        WalletData.get(player.server).set(player.getUUID(), 5);

        WalletActions.WithdrawResult result = WalletActions.withdraw(player, 5);
        helper.assertTrue(inv.getItem(40).getCount() == 6, "Diamonds merge into the offhand stack");
        helper.assertTrue(result.given() == 5, "All 5 were given, got " + result.given());
        helper.assertTrue(balance(player) == 0, "No refund for items given, balance " + balance(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withdrawAllWithEmptyWalletSaysSo(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        helper.assertTrue(WalletActions.withdrawAll(player).status() == WalletActions.WithdrawStatus.INSUFFICIENT,
                "An empty wallet has nothing to take out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryLeavesTheRestInTheWalletWithNothingDroppedOrLost(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        int droppedBefore = droppedItems(helper);

        for (int i = 0; i < 36; i++) {
            inv.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        WalletData.get(player.server).set(player.getUUID(), 20);
        WalletActions.WithdrawResult none = WalletActions.withdraw(player, 20);
        helper.assertTrue(none.given() == 0 && balance(player) == 20, "Nothing fits, all stays in the wallet");
        helper.assertTrue(count(inv, Items.DIAMOND_BLOCK) == 0 && count(inv, Items.DIAMOND) == 0, "No items given");

        inv.setItem(35, ItemStack.EMPTY);
        WalletActions.WithdrawResult some = WalletActions.withdraw(player, 20);
        long total = some.given() + balance(player);
        helper.assertTrue(total == 20, "Nothing lost, total was " + total);
        helper.assertTrue(some.given() == 18 && count(inv, Items.DIAMOND_BLOCK) == 2, "Blocks fit in the free slot");
        helper.assertTrue(balance(player) == 2, "Remaining 2 stay in the wallet");
        helper.assertTrue(droppedItems(helper) == droppedBefore, "Nothing may be dropped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withdrawingTheMostFinishesAndTheTotalStaysCorrect(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        Inventory inv = player.getInventory();
        WalletData.get(player.server).set(player.getUUID(), Long.MAX_VALUE);

        WalletActions.WithdrawResult result = WalletActions.withdrawAll(player);
        long given = (long) count(inv, Items.DIAMOND_BLOCK) * 9 + count(inv, Items.DIAMOND);
        helper.assertTrue(given == result.given(), "Reported value must match items, " + given + " vs " + result.given());
        helper.assertTrue(given > 0, "Something was given");
        helper.assertTrue(balance(player) + given == Long.MAX_VALUE, "Total must stay Long.MAX_VALUE");
        WalletData.get(player.server).set(player.getUUID(), 0);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void walletDataSurvivesSaveAndLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        WalletData data = new WalletData();
        data.set(a, Long.MAX_VALUE - 1);
        data.set(b, 5);

        CompoundTag tag = data.save(new CompoundTag(), registries);
        WalletData loaded = WalletData.load(tag, registries);
        helper.assertTrue(loaded.balance(a) == Long.MAX_VALUE - 1, "Large balance must survive");
        helper.assertTrue(loaded.balance(b) == 5, "Small balance must survive");
        helper.assertTrue(loaded.balance(UUID.randomUUID()) == 0, "Unknown player has 0");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newerFormatVersionAndBrokenDataMakeTheLoaderThrow(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag newer = new WalletData().save(new CompoundTag(), registries);
        newer.putInt("version", WalletData.FORMAT_VERSION + 1);
        helper.assertTrue(throwsOnLoad(newer, helper), "Newer version must throw");

        helper.assertTrue(throwsOnLoad(new CompoundTag(), helper), "No version must throw");

        CompoundTag noList = new CompoundTag();
        noList.putInt("version", WalletData.FORMAT_VERSION);
        helper.assertTrue(throwsOnLoad(noList, helper), "No wallet list must throw");

        CompoundTag oneBad = new WalletData().save(new CompoundTag(), registries);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("player", "not a uuid");
        bad.putLong("balance", 5);
        UUID good = UUID.randomUUID();
        CompoundTag ok = new CompoundTag();
        ok.putString("player", good.toString());
        ok.putLong("balance", 9);
        list.add(bad);
        list.add(ok);
        oneBad.put("wallets", list);
        helper.assertTrue(WalletData.load(oneBad, registries).balance(good) == 9, "One bad entry is skipped, the rest stays");
        helper.succeed();
    }

    private static boolean throwsOnLoad(CompoundTag tag, GameTestHelper helper) {
        try {
            WalletData.load(tag, helper.getLevel().registryAccess());
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @GameTest(template = "empty")
    public static void adminCommandsRefuseAPlayerWithoutPermission(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        var commands = player.server.getCommands();

        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "market admin give @s 50");
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "markettrade admin set @s 50");
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "market admin reload");
        helper.assertTrue(balance(player) == 0, "No permission, no change");

        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "market admin give @s 50");
        helper.assertTrue(balance(player) == 50, "Permission level 2 can give, got " + balance(player));
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "markettrade admin take @s 20");
        helper.assertTrue(balance(player) == 30, "Take works");
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "market admin take @s 31");
        helper.assertTrue(balance(player) == 30, "Taking too much changes nothing");
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "market admin set @s 7");
        helper.assertTrue(balance(player) == 7, "Set works");
        WalletData.get(player.server).set(player.getUUID(), 0);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerCommandsWorkAndConsoleGetsPlayersOnly(GameTestHelper helper) {
        ServerPlayer player = setUp(helper);
        var commands = player.server.getCommands();
        WalletData.get(player.server).set(player.getUUID(), 20);

        commands.performPrefixedCommand(player.createCommandSourceStack(), "market withdraw 10");
        helper.assertTrue(count(player.getInventory(), Items.DIAMOND_BLOCK) == 1
                && count(player.getInventory(), Items.DIAMOND) == 1, "Withdraw command gives items");
        helper.assertTrue(balance(player) == 10, "Wallet is down by 10");
        commands.performPrefixedCommand(player.createCommandSourceStack(), "markettrade deposit all");
        helper.assertTrue(balance(player) == 20, "Deposit all command puts it back");
        commands.performPrefixedCommand(player.createCommandSourceStack(), "market balance");

        var server = player.server;
        commands.performPrefixedCommand(server.createCommandSourceStack(), "market balance");
        commands.performPrefixedCommand(server.createCommandSourceStack(), "market withdraw all");
        WalletData.get(server).set(player.getUUID(), 0);
        helper.succeed();
    }
}
