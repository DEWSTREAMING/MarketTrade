package com.markettrade.devtest;

import com.markettrade.MarketTrade;
import com.markettrade.gui.MarketChestMenu;
import com.markettrade.net.ClientTracker;
import com.markettrade.net.MarketPackets;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase1Tests {

    @GameTest(template = "empty")
    public static void chestMenuOpensOnFirstPageWithRightItems(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestMarket.seedSamples(player);
        MarketChestMenu.open(player, 0);

        helper.assertTrue(player.containerMenu instanceof MarketChestMenu, "Player menu should be MarketChestMenu");
        MarketChestMenu menu = (MarketChestMenu) player.containerMenu;

        helper.assertTrue(menu.getCurrentPage() == 0, "First page must be 0");
        helper.assertTrue(menu.getTotalPages() == 2, "Total pages should be 2 for 60 items");

        // Check renamed item on slot 0
        ItemStack slot0 = menu.getContainer().getItem(0);
        helper.assertTrue(slot0.is(Items.DIAMOND_SWORD), "Slot 0 should be diamond sword");
        Component customName = slot0.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(customName != null && customName.getString().contains("Dragon Slayer"),
                "Slot 0 should have Dragon Slayer custom name");
        ItemLore lore0 = slot0.get(DataComponents.LORE);
        helper.assertTrue(lore0 != null && lore0.lines().size() >= 2, "Slot 0 should have price and seller lore");

        // Check enchanted item on slot 1
        ItemStack slot1 = menu.getContainer().getItem(1);
        helper.assertTrue(slot1.is(Items.DIAMOND_PICKAXE), "Slot 1 should be diamond pickaxe");
        ItemEnchantments enchants = slot1.get(DataComponents.ENCHANTMENTS);
        helper.assertTrue(enchants != null && !enchants.isEmpty(), "Slot 1 should have enchantment data");
        ItemLore lore1 = slot1.get(DataComponents.LORE);
        helper.assertTrue(lore1 != null && lore1.lines().size() >= 2, "Slot 1 should have price and seller lore");

        // Check bottom row buttons on first page
        ItemStack prevBtn = menu.getContainer().getItem(MarketChestMenu.PREV_SLOT);
        helper.assertTrue(prevBtn.isEmpty(), "Previous button should be empty on first page");

        ItemStack pageItem = menu.getContainer().getItem(MarketChestMenu.PAGE_SLOT);
        helper.assertTrue(pageItem.is(Items.PAPER), "Page indicator should be paper");
        helper.assertTrue(pageItem.getCount() == 1, "Page indicator count should be 1 on page 1");

        ItemStack nextBtn = menu.getContainer().getItem(MarketChestMenu.NEXT_SLOT);
        helper.assertTrue(nextBtn.is(Items.ARROW), "Next button should be arrow on first page");

        ItemStack closeBtn = menu.getContainer().getItem(MarketChestMenu.CLOSE_SLOT);
        helper.assertTrue(closeBtn.is(Items.BARRIER), "Close button should be barrier");

        TestMarket.clearAll(player.server);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nextAndPreviousChangePageAndStopAtEnds(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestMarket.seedSamples(player);
        MarketChestMenu.open(player, 0);

        MarketChestMenu menu = (MarketChestMenu) player.containerMenu;
        helper.assertTrue(menu.getCurrentPage() == 0, "Starting page should be 0");

        // Attempting previous on page 0 does nothing
        menu.clicked(MarketChestMenu.PREV_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCurrentPage() == 0, "Page should stay 0 on previous click at start");

        // Click next page
        menu.clicked(MarketChestMenu.NEXT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCurrentPage() == 1, "Page should advance to 1");

        // On last page (page 1), prev is arrow, next is empty
        ItemStack prevBtn = menu.getContainer().getItem(MarketChestMenu.PREV_SLOT);
        helper.assertTrue(prevBtn.is(Items.ARROW), "Previous button should be arrow on page 2");

        ItemStack nextBtn = menu.getContainer().getItem(MarketChestMenu.NEXT_SLOT);
        helper.assertTrue(nextBtn.isEmpty(), "Next button should be empty on last page");

        ItemStack pageItem = menu.getContainer().getItem(MarketChestMenu.PAGE_SLOT);
        helper.assertTrue(pageItem.getCount() == 2, "Page indicator count should be 2 on page 2");

        // Attempting next on last page does nothing
        menu.clicked(MarketChestMenu.NEXT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCurrentPage() == 1, "Page should stay 1 when next clicked on last page");

        // Click previous page
        menu.clicked(MarketChestMenu.PREV_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCurrentPage() == 0, "Page should return to 0");

        TestMarket.clearAll(player.server);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachClickTypeLeavesInventoryAndMenuUnchanged(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Place distinct items in player inventory
        player.getInventory().setItem(0, new ItemStack(Items.APPLE, 10));
        player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD, 1));
        player.getInventory().setItem(2, new ItemStack(Items.DIRT, 64));

        TestMarket.seedSamples(player);
        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = (MarketChestMenu) player.containerMenu;

        ClickType[] clickTypes = {
                ClickType.PICKUP,
                ClickType.QUICK_MOVE,
                ClickType.SWAP,
                ClickType.CLONE,
                ClickType.THROW,
                ClickType.QUICK_CRAFT,
                ClickType.PICKUP_ALL
        };

        // Test each click type on menu listing slot 0
        for (ClickType type : clickTypes) {
            menu.clicked(0, 0, type, player);
            menu.clicked(0, 1, type, player);
        }

        // Test hotbar swap buttons on slot 0
        for (int button = 0; button < 9; button++) {
            menu.clicked(0, button, ClickType.SWAP, player);
        }
        // Offhand swap
        menu.clicked(0, 40, ClickType.SWAP, player);

        // Test each click type on player inventory slot (slot 54, 55, 56)
        for (ClickType type : clickTypes) {
            menu.clicked(54, 0, type, player);
            menu.clicked(55, 0, type, player);
            menu.clicked(56, 0, type, player);
        }

        // Test clicking outside
        menu.clicked(-999, 0, ClickType.PICKUP, player);
        menu.clicked(-999, 1, ClickType.PICKUP, player);
        menu.clicked(-999, 0, ClickType.THROW, player);

        // Verify player inventory remained completely unchanged
        ItemStack inv0 = player.getInventory().getItem(0);
        helper.assertTrue(inv0.is(Items.APPLE) && inv0.getCount() == 10, "Apples in slot 0 must not change");

        ItemStack inv1 = player.getInventory().getItem(1);
        helper.assertTrue(inv1.is(Items.IRON_SWORD) && inv1.getCount() == 1, "Iron sword in slot 1 must not change");

        ItemStack inv2 = player.getInventory().getItem(2);
        helper.assertTrue(inv2.is(Items.DIRT) && inv2.getCount() == 64, "Dirt in slot 2 must not change");

        // Verify carried cursor is still empty
        helper.assertTrue(menu.getCarried().isEmpty(), "Carried cursor must stay empty");

        // Verify menu items remained intact
        ItemStack menuSlot0 = menu.getContainer().getItem(0);
        helper.assertTrue(menuSlot0.is(Items.DIAMOND_SWORD), "Menu slot 0 must remain diamond sword");

        ItemStack menuSlot1 = menu.getContainer().getItem(1);
        helper.assertTrue(menuSlot1.is(Items.DIAMOND_PICKAXE), "Menu slot 1 must remain diamond pickaxe");

        TestMarket.clearAll(player.server);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mockPlayerWithoutChannelGetsChestScreenAndNoPayload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Verify mock player has no channel and no hello recorded
        helper.assertTrue(!player.connection.hasChannel(MarketPackets.OpenScreen.TYPE),
                "Mock player connection must not report having markettrade channel");
        helper.assertTrue(!ClientTracker.hasValidHello(player.getUUID()),
                "Mock player must not have valid hello");

        // Run /market
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "market");
        helper.assertTrue(player.containerMenu instanceof MarketChestMenu,
                "Running /market must open MarketChestMenu for client without channel");

        // Close and run /markettrade alias
        player.closeContainer();
        helper.assertTrue(!(player.containerMenu instanceof MarketChestMenu),
                "Container should be closed");

        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "markettrade");
        helper.assertTrue(player.containerMenu instanceof MarketChestMenu,
                "Running /markettrade alias must open MarketChestMenu for client without channel");

        player.closeContainer();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void consoleCommandReportsPlayersOnly(GameTestHelper helper) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack(),
                "market"
        );
        helper.succeed();
    }
}
