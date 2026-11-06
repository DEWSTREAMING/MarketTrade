package com.markettrade.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.gui.MarketChestMenu;
import com.markettrade.gui.TextEntryMenu;
import com.markettrade.market.Cut;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.StoredStack;
import com.markettrade.market.WalletData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chest screens. Tests that wait for the next tick (anything that goes through the anvil) have a batch of their own, because
 * all tests share one market and the batches run one after the other.
 */
@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase4Tests {
    private static final int SORT = MarketChestMenu.SORT_SLOT;
    private static final int SEARCH = MarketChestMenu.SEARCH_SLOT;
    private static final int MINE = MarketChestMenu.MY_LISTINGS_SLOT;
    private static final int RETURNS = MarketChestMenu.RETURNS_SLOT;
    private static final int WALLET = MarketChestMenu.WALLET_SLOT;
    private static final int NEXT = MarketChestMenu.NEXT_SLOT;
    private static final int CONFIRM = 46;
    private static final int GO_BACK = 52;

    // Setup

    private static CurrencyConfig coins() {
        return new CurrencyConfig("coins", List.of(
                new CurrencyItem("minecraft:gold_ingot", 64, "gold"),
                new CurrencyItem("minecraft:iron_ingot", 8, "silver"),
                new CurrencyItem("minecraft:copper_ingot", 1, "copper")));
    }

    private static void setup(GameTestHelper helper, CurrencyConfig currency) {
        Currencies.set(currency);
        MarketConfigs.set(MarketConfig.defaults());
        TestMarket.clearAll(helper.getLevel().getServer());
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        MarketData.get(player.server).replaceReturns(player.getUUID(), List.of());
        return player;
    }

    private static long wallet(ServerPlayer player) {
        return WalletData.get(player.server).balance(player.getUUID());
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            player.closeContainer();
            MarketData.get(player.server).replaceReturns(player.getUUID(), List.of());
            WalletData.get(player.server).set(player.getUUID(), 0);
        }
        TestMarket.clearAll(helper.getLevel().getServer());
        helper.succeed();
    }

    private static void after(GameTestHelper helper, Runnable next) {
        helper.runAfterDelay(2, next);
    }

    private static ItemStack fancySword(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Blade"));
        helper.getLevel().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(Enchantments.SHARPNESS)).ifPresent(holder -> {
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mutable.set(holder, 4);
                    sword.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                });
        return sword;
    }

    private static ItemStack named(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }

    private static boolean loreHas(ItemStack stack, String part) {
        return lore(stack).stream().anyMatch(line -> line.contains(part));
    }

    private static String nameOf(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private static String show(long amount) {
        return Currencies.current().format(amount);
    }

    private static MarketChestMenu chest(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.containerMenu instanceof MarketChestMenu, "The chest screen should be open");
        return (MarketChestMenu) player.containerMenu;
    }

    private static TextEntryMenu anvil(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.containerMenu instanceof TextEntryMenu, "The text screen should be open");
        return (TextEntryMenu) player.containerMenu;
    }

    private static ItemStack at(AbstractContainerMenu menu, int slot) {
        return menu.getSlot(slot).getItem();
    }

    /** Confirm Buy and Confirm Sell both have green glass in the bottom row. */
    private static boolean isConfirmScreen(AbstractContainerMenu menu) {
        return at(menu, 45).is(Items.LIME_STAINED_GLASS_PANE);
    }

    private static List<ItemStack> inventoryOf(ServerPlayer player) {
        List<ItemStack> copy = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            copy.add(inventory.getItem(i).copy());
        }
        return copy;
    }

    private static List<ItemStack> slotsOf(AbstractContainerMenu menu) {
        List<ItemStack> copy = new ArrayList<>();
        for (Slot slot : menu.slots) {
            copy.add(slot.getItem().copy());
        }
        return copy;
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void fillWithJunk(Inventory inventory) {
        Item[] junk = {Items.DIRT, Items.COBBLESTONE, Items.STICK, Items.APPLE, Items.BREAD, Items.COAL, Items.FLINT};
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, new ItemStack(junk[i % junk.length], 1 + i));
        }
    }

    /** -999 is a click outside the window, then every slot. */
    private static int[] slotRange(int slotCount) {
        int[] range = new int[slotCount + 1];
        range[0] = -999;
        for (int i = 0; i < slotCount; i++) {
            range[i + 1] = i;
        }
        return range;
    }

    private static final ClickType[] QUIET_TYPES = {ClickType.SWAP, ClickType.THROW, ClickType.CLONE, ClickType.QUICK_CRAFT,
            ClickType.PICKUP_ALL};

    /** Types that never do anything. Nothing at all may change: not the screen, not the inventory, not the cursor. */
    private static void sweepQuietTypes(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu, String where) {
        List<ItemStack> inventoryBefore = inventoryOf(player);
        List<ItemStack> menuBefore = slotsOf(menu);
        for (ClickType type : QUIET_TYPES) {
            for (int slot : slotRange(menu.slots.size())) {
                for (int button = 0; button < 9; button++) {
                    menu.clicked(slot, button, type, player);
                }
                menu.clicked(slot, 40, type, player);
            }
        }
        helper.assertTrue(player.containerMenu == menu, where + ": the screen must not change");
        helper.assertTrue(same(inventoryBefore, inventoryOf(player)), where + ": the inventory must not change");
        helper.assertTrue(same(menuBefore, slotsOf(menu)), where + ": the slots must not change");
        helper.assertTrue(menu.getCarried().isEmpty(), where + ": the cursor must stay empty");
    }

    // Browse

    @GameTest(template = "empty")
    public static void browseShowsRealListingsWithPriceSellerAndTimeLore(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer viewer = player(helper);
        ItemStack sword = fancySword(helper);
        sword.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Forged in fire"))));
        TestMarket.add(seller, new ItemStack(Items.APPLE, 5), 7, 3);
        TestMarket.add(seller, sword.copy(), 40);

        MarketChestMenu.open(viewer, 0);
        MarketChestMenu menu = chest(helper, viewer);
        ItemStack first = at(menu, 0);
        helper.assertTrue(first.is(Items.DIAMOND_SWORD) && first.getCount() == 1, "Newest listing first, a sword");
        helper.assertTrue(nameOf(first).equals("Lucky Blade"), "Custom name is kept, got " + nameOf(first));
        helper.assertTrue(java.util.Objects.equals(first.get(DataComponents.ENCHANTMENTS), sword.get(DataComponents.ENCHANTMENTS)),
                "Enchantments are kept");
        List<String> lines = lore(first);
        helper.assertTrue(lines.get(0).equals("Forged in fire"), "The item's own lore stays first, got " + lines);
        helper.assertTrue(loreHas(first, "Price: " + show(40)), "Price line, got " + lines);
        helper.assertTrue(loreHas(first, "Seller: " + seller.getGameProfile().getName()), "Seller line, got " + lines);
        helper.assertTrue(loreHas(first, "Time left: 7d 0h"), "Time left line, got " + lines);

        ItemStack second = at(menu, 1);
        helper.assertTrue(second.is(Items.APPLE) && second.getCount() == 5, "Count is kept");
        helper.assertTrue(loreHas(second, "Time left: 3d 0h"), "Time left for 3 days, got " + lore(second));
        helper.assertTrue(at(menu, 2).isEmpty(), "Only two listings");
        helper.assertTrue(nameOf(at(menu, MarketChestMenu.RETURNS_SLOT)).contains("Returns"), "Returns button");
        helper.assertTrue(nameOf(at(menu, MarketChestMenu.WALLET_SLOT)).contains("Wallet"), "Wallet button");
        finish(helper, seller, viewer);
    }

    @GameTest(template = "empty")
    public static void loreIsNotAddedToAStackThatIsAlreadyFull(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer viewer = player(helper);
        ItemStack full = new ItemStack(Items.PAPER);
        full.set(DataComponents.LORE, new ItemLore(IntStream.range(0, ItemLore.MAX_LINES)
                .mapToObj(i -> (Component) Component.literal("line " + i)).toList()));
        ItemStack nearly = new ItemStack(Items.STICK);
        nearly.set(DataComponents.LORE, new ItemLore(IntStream.range(0, ItemLore.MAX_LINES - 1)
                .mapToObj(i -> (Component) Component.literal("line " + i)).toList()));
        TestMarket.add(seller, full, 5);
        TestMarket.add(seller, nearly, 5);

        MarketChestMenu.open(viewer, 0);
        MarketChestMenu menu = chest(helper, viewer);
        helper.assertTrue(lore(at(menu, 0)).size() == ItemLore.MAX_LINES, "An almost full stack gets lines up to the limit");
        helper.assertTrue(lore(at(menu, 0)).get(255).startsWith("Price"), "The price is the line that fits");
        helper.assertTrue(lore(at(menu, 1)).size() == ItemLore.MAX_LINES && lore(at(menu, 1)).get(255).equals("line 255"),
                "A full stack gets nothing added");
        finish(helper, seller, viewer);
    }

    @GameTest(template = "empty")
    public static void unavailableListingShowsAsABarrierAndCantBeBought(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        WalletData.get(buyer.server).set(buyer.getUUID(), 1000);
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "gone:mystery_item");
        raw.putInt("count", 3);
        MarketData market = MarketData.get(seller.server);
        long id = market.nextId();
        market.addListing(new Listing(id, seller.getUUID(), "Ghost", StoredStack.unavailable(raw), 20,
                System.currentTimeMillis() + TestMarket.DAY));

        MarketChestMenu.open(buyer, 0);
        MarketChestMenu menu = chest(helper, buyer);
        ItemStack shown = at(menu, 0);
        helper.assertTrue(shown.is(Items.BARRIER), "An unavailable listing is a barrier");
        helper.assertTrue(nameOf(shown).equals("gone:mystery_item"), "Named with its item id, got " + nameOf(shown));
        helper.assertTrue(loreHas(shown, "Not available"), "Lore says it is unavailable, got " + lore(shown));

        TestMarket.click(menu, buyer, 0);
        helper.assertTrue(!isConfirmScreen(menu), "No confirm screen for an unavailable listing");
        TestMarket.click(menu, buyer, 0, 0, ClickType.QUICK_MOVE);
        helper.assertTrue(!isConfirmScreen(menu), "Shift-click doesn't open it either");
        helper.assertTrue(MarketActions.buy(buyer, id, 20, System.currentTimeMillis()).status()
                == MarketActions.BuyStatus.UNAVAILABLE, "The action refuses it as well");
        helper.assertTrue(market.listing(id) != null && wallet(buyer) == 1000, "Nothing changed");
        finish(helper, seller, buyer);
    }

    @GameTest(template = "empty")
    public static void sortCyclesThroughAllFiveOrders(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer viewer = player(helper);
        TestMarket.add(seller, named(Items.COBBLESTONE, 1, "Zebra"), 50, 5);
        TestMarket.add(seller, new ItemStack(Items.APPLE), 10, 2);
        TestMarket.add(seller, new ItemStack(Items.DIAMOND), 90, 6);

        MarketChestMenu.open(viewer, 0);
        MarketChestMenu menu = chest(helper, viewer);
        String[][] expected = {
                {"Newest", "Diamond", "Apple", "Zebra"},
                {"Cheapest", "Apple", "Zebra", "Diamond"},
                {"Dearest", "Diamond", "Zebra", "Apple"},
                {"Ending soon", "Apple", "Zebra", "Diamond"},
                {"Name", "Apple", "Diamond", "Zebra"},
                {"Newest", "Diamond", "Apple", "Zebra"}};
        for (int step = 0; step < expected.length; step++) {
            if (step > 0) {
                TestMarket.click(menu, viewer, SORT);
            }
            helper.assertTrue(nameOf(at(menu, SORT)).equals("Sort: " + expected[step][0]),
                    "Sort button should say " + expected[step][0] + ", got " + nameOf(at(menu, SORT)));
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(nameOf(at(menu, slot)).equals(expected[step][slot + 1]),
                        expected[step][0] + " slot " + slot + " should be " + expected[step][slot + 1] + ", got "
                                + nameOf(at(menu, slot)));
            }
        }
        finish(helper, seller, viewer);
    }

    @GameTest(template = "empty")
    public static void pagesWorkAndThePageItemCountStaysValidOnAVeryHighPage(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer viewer = player(helper);
        for (int i = 0; i < 3200; i++) {
            TestMarket.add(seller, new ItemStack(Items.STICK), 1 + i);
        }
        MarketChestMenu.open(viewer, 0);
        MarketChestMenu menu = chest(helper, viewer);
        TestMarket.click(menu, viewer, NEXT);
        TestMarket.click(menu, viewer, NEXT);
        helper.assertTrue(menu.getCurrentPage() == 2 && at(menu, MarketChestMenu.PAGE_SLOT).getCount() == 3,
                "Two clicks on next make page 3");
        TestMarket.click(menu, viewer, MarketChestMenu.PREV_SLOT);
        helper.assertTrue(menu.getCurrentPage() == 1, "Previous goes back");

        viewer.closeContainer();
        MarketChestMenu.open(viewer, 100_000);
        MarketChestMenu last = chest(helper, viewer);
        int pages = last.getTotalPages();
        helper.assertTrue(pages == 72, "3200 listings are 72 pages, got " + pages);
        helper.assertTrue(last.getCurrentPage() == pages - 1, "A page past the end clamps to the last");
        int count = at(last, MarketChestMenu.PAGE_SLOT).getCount();
        helper.assertTrue(count >= 1 && count <= 64, "The page item count must stay valid, got " + count);
        helper.assertTrue(at(last, NEXT).isEmpty(), "No next button on the last page");
        helper.assertTrue(!at(last, MarketChestMenu.PREV_SLOT).isEmpty(), "There is a previous button");
        finish(helper, seller, viewer);
    }

    // Clicks

    private static void prepareForSweeps(ServerPlayer viewer, ServerPlayer other) {
        TestMarket.clearAll(viewer.server);
        TestMarket.add(viewer, new ItemStack(Items.STICK, 2), 5);
        TestMarket.add(other, new ItemStack(Items.APPLE, 2), 10);
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "gone:mystery_item");
        raw.putInt("count", 1);
        MarketData.get(viewer.server).replaceReturns(viewer.getUUID(), List.of(StoredStack.unavailable(raw)));
        WalletData.get(viewer.server).set(viewer.getUUID(), 0);
        viewer.getInventory().clearContent();
        fillWithJunk(viewer.getInventory());
    }

    private static MarketChestMenu openView(ServerPlayer player, String view) {
        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = (MarketChestMenu) player.containerMenu;
        switch (view) {
            case "mine" -> TestMarket.click(menu, player, MINE);
            case "returns" -> TestMarket.click(menu, player, RETURNS);
            case "wallet" -> TestMarket.click(menu, player, WALLET);
            case "confirmBuy" -> TestMarket.click(menu, player, 0);
            default -> {
            }
        }
        return menu;
    }

    @GameTest(template = "empty")
    public static void everyClickOnEveryViewLeavesTheInventoryAndCursorAlone(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer viewer = player(helper);
        ServerPlayer other = player(helper);
        String[] views = {"browse", "mine", "returns", "wallet", "confirmBuy"};

        for (String view : views) {
            // Types that do nothing, on one screen, over every slot and button.
            prepareForSweeps(viewer, other);
            MarketChestMenu menu = openView(viewer, view);
            if (view.equals("confirmBuy")) {
                helper.assertTrue(isConfirmScreen(menu), "The confirm buy screen should be showing");
            }
            sweepQuietTypes(helper, viewer, menu, view);

            // Types that act: a fresh screen for every click, set up so no action may change the inventory.
            for (ClickType type : new ClickType[] {ClickType.PICKUP, ClickType.QUICK_MOVE}) {
                for (int slot : slotRange(90)) {
                    for (int button = 0; button < 2; button++) {
                        prepareForSweeps(viewer, other);
                        List<ItemStack> before = inventoryOf(viewer);
                        MarketChestMenu fresh = openView(viewer, view);
                        TestMarket.click(fresh, viewer, slot, button, type);
                        helper.assertTrue(same(before, inventoryOf(viewer)),
                                view + " " + type + " slot " + slot + " button " + button + " changed the inventory");
                        helper.assertTrue(fresh.getCarried().isEmpty() && viewer.containerMenu.getCarried().isEmpty(),
                                view + " " + type + " slot " + slot + " left something on the cursor");
                    }
                }
            }
        }
        finish(helper, viewer, other);
    }

    @GameTest(template = "empty")
    public static void aStaleSlotActsOnTheListingDrawnThereOrRefuses(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer other = player(helper);
        ServerPlayer buyer = player(helper);
        WalletData.get(buyer.server).set(buyer.getUUID(), 500);
        MarketData market = MarketData.get(buyer.server);
        TestMarket.add(seller, new ItemStack(Items.GOLD_INGOT, 3), 50);

        // The list changes under an open screen: a newer listing takes slot 0 in the market, but nothing is redrawn.
        MarketChestMenu.open(buyer, 0);
        MarketChestMenu menu = chest(helper, buyer);
        long newer = TestMarket.add(other, new ItemStack(Items.EMERALD, 9), 20);
        TestMarket.click(menu, buyer, 0);
        helper.assertTrue(isConfirmScreen(menu), "The click opens a confirm screen for the listing that was drawn");
        TestMarket.click(menu, buyer, CONFIRM);
        helper.assertTrue(count(buyer.getInventory(), Items.GOLD_INGOT) == 3, "The buyer got the drawn listing");
        helper.assertTrue(count(buyer.getInventory(), Items.EMERALD) == 0 && market.listing(newer) != null,
                "The newer listing at that position was not touched");
        helper.assertTrue(wallet(buyer) == 450, "Paid the drawn price only, wallet " + wallet(buyer));

        // The drawn listing is gone by the time of the click.
        long vanishing = TestMarket.add(seller, new ItemStack(Items.IRON_INGOT, 4), 30);
        MarketChestMenu.open(buyer, 0);
        MarketChestMenu second = chest(helper, buyer);
        market.removeListing(vanishing);
        TestMarket.click(second, buyer, 0);
        helper.assertTrue(!isConfirmScreen(second), "A listing that is gone can't be bought");
        helper.assertTrue(count(buyer.getInventory(), Items.IRON_INGOT) == 0 && wallet(buyer) == 450, "Nothing moved");

        // Its price changed: the same id comes back at a new price.
        long repriced = TestMarket.add(seller, new ItemStack(Items.COAL, 6), 10);
        MarketChestMenu.open(buyer, 0);
        MarketChestMenu fourth = chest(helper, buyer);
        Listing old = market.removeListing(repriced);
        market.addListing(new Listing(old.id(), old.seller(), old.sellerName(), old.item(), 400, old.expiresAt()));
        TestMarket.click(fourth, buyer, 0);
        helper.assertTrue(!isConfirmScreen(fourth), "A changed price goes back to the list instead of the confirm screen");
        helper.assertTrue(count(buyer.getInventory(), Items.COAL) == 0 && wallet(buyer) == 450, "Nothing moved");
        finish(helper, seller, other, buyer);
    }

    @GameTest(template = "empty")
    public static void confirmBuyWhereTheListingSoldInTheMeantimeChangesNothing(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer rival = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(buyer.server);
        WalletData.get(buyer.server).set(buyer.getUUID(), 500);
        WalletData.get(buyer.server).set(rival.getUUID(), 500);
        long id = TestMarket.add(seller, fancySword(helper), 100);

        MarketChestMenu.open(buyer, 0);
        MarketChestMenu menu = chest(helper, buyer);
        TestMarket.click(menu, buyer, 0);
        helper.assertTrue(isConfirmScreen(menu), "Confirm screen is up");
        helper.assertTrue(MarketActions.buy(rival, id, 100, System.currentTimeMillis()).status() == MarketActions.BuyStatus.OK,
                "The rival buys it first");
        long poolBefore = market.pool();
        TestMarket.click(menu, buyer, CONFIRM);

        helper.assertTrue(wallet(buyer) == 500, "The late buyer paid nothing, got " + wallet(buyer));
        helper.assertTrue(count(buyer.getInventory(), Items.DIAMOND_SWORD) == 0, "And got nothing");
        helper.assertTrue(market.pool() == poolBefore && wallet(seller) == 95, "Pool and seller are as the rival's sale left them");
        helper.assertTrue(!isConfirmScreen(menu), "Back on the list");
        helper.assertTrue(at(menu, 0).isEmpty(), "The list was redrawn without the sold listing");
        finish(helper, seller, rival, buyer);
    }

    @GameTest(template = "empty")
    public static void confirmBuyChargesTheDrawnPriceAndGoesBack(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(buyer.server);
        WalletData.get(buyer.server).set(buyer.getUUID(), 500);
        long id = TestMarket.add(seller, fancySword(helper), 100);

        MarketChestMenu.open(buyer, 0);
        MarketChestMenu menu = chest(helper, buyer);
        TestMarket.click(menu, buyer, 0);
        helper.assertTrue(isConfirmScreen(menu), "Confirm screen is up");
        helper.assertTrue(at(menu, 22).is(Items.DIAMOND_SWORD) && loreHas(at(menu, 22), "Price: " + show(100)),
                "The item and price are shown");
        helper.assertTrue(at(menu, 51).is(Items.RED_STAINED_GLASS_PANE), "Red glass goes back");
        TestMarket.click(menu, buyer, GO_BACK);
        helper.assertTrue(!isConfirmScreen(menu) && market.listing(id) != null && wallet(buyer) == 500,
                "Going back changes nothing");

        TestMarket.click(menu, buyer, 0);
        TestMarket.click(menu, buyer, 47, 0, ClickType.QUICK_MOVE);
        helper.assertTrue(market.listing(id) == null && wallet(buyer) == 400, "Bought at the drawn price");
        helper.assertTrue(ItemStack.matches(buyer.getInventory().getItem(0), fancySword(helper)), "The sword arrived whole");
        helper.assertTrue(!isConfirmScreen(menu), "Back on the list");
        finish(helper, seller, buyer);
    }

    // My Listings, Returns, Wallet

    @GameTest(template = "empty")
    public static void myListingsCancelSendsTheItemToReturns(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer player = player(helper);
        ServerPlayer other = player(helper);
        MarketData market = MarketData.get(player.server);
        long oldest = TestMarket.add(player, fancySword(helper), 40);
        long middle = TestMarket.add(player, new ItemStack(Items.APPLE, 4), 5);
        long newest = TestMarket.add(player, new ItemStack(Items.BREAD, 4), 6);
        TestMarket.add(other, new ItemStack(Items.COAL), 2);
        List<ItemStack> inventory = inventoryOf(player);

        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = chest(helper, player);
        TestMarket.click(menu, player, MINE);
        helper.assertTrue(at(menu, 0).is(Items.BREAD) && at(menu, 2).is(Items.DIAMOND_SWORD) && at(menu, 3).isEmpty(),
                "Only the player's own three listings show");
        helper.assertTrue(loreHas(at(menu, 2), "Time left: 7d 0h") && loreHas(at(menu, 2), "Price: " + show(40)),
                "Price and time left show");

        TestMarket.click(menu, player, 2);
        helper.assertTrue(market.listing(oldest) == null, "The clicked listing is cancelled");
        helper.assertTrue(market.returnsOf(player.getUUID()).size() == 1
                && ItemStack.matches(market.returnsOf(player.getUUID()).get(0).stack(), fancySword(helper)),
                "The sword is in Returns with its data");
        helper.assertTrue(same(inventory, inventoryOf(player)), "The inventory did not change");
        helper.assertTrue(at(menu, 1).is(Items.APPLE) && at(menu, 2).isEmpty(), "The list was redrawn");

        // A double click sends two clicks before the redraw arrives. The second must not cancel what slid into the slot.
        TestMarket.click(menu, player, 0);
        TestMarket.click(menu, player, 0);
        helper.assertTrue(market.listing(newest) == null && market.listing(middle) != null,
                "Two quick clicks on one slot cancel only one listing");
        TestMarket.click(menu, player, MarketChestMenu.BACK_SLOT);
        helper.assertTrue(nameOf(at(menu, SORT)).startsWith("Sort"), "Back returns to Browse");
        finish(helper, player, other);
    }

    @GameTest(template = "empty")
    public static void returnsClaimOneAndClaimAll(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer player = player(helper);
        MarketData market = MarketData.get(player.server);
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "gone:mystery_item");
        raw.putInt("count", 2);
        market.addReturn(player.getUUID(), StoredStack.of(named(Items.DIRT, 3, "Special dirt")));
        market.addReturn(player.getUUID(), StoredStack.of(fancySword(helper)));
        market.addReturn(player.getUUID(), StoredStack.unavailable(raw));
        market.addReturn(player.getUUID(), StoredStack.of(new ItemStack(Items.BREAD, 5)));

        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = chest(helper, player);
        helper.assertTrue(at(menu, RETURNS).getCount() == 4, "The Returns button counts the stacks");
        TestMarket.click(menu, player, RETURNS);
        helper.assertTrue(at(menu, 0).is(Items.DIRT) && at(menu, 2).is(Items.BARRIER) && at(menu, 3).is(Items.BREAD),
                "One slot per stack, an unavailable one as a barrier");
        helper.assertTrue(nameOf(at(menu, 2)).equals("gone:mystery_item"), "The barrier is named with the item id");

        TestMarket.click(menu, player, 1);
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), fancySword(helper)), "The sword came whole");
        helper.assertTrue(market.returnsOf(player.getUUID()).size() == 3, "Only that stack left Returns");

        // Stale: the list changed under the screen. Slot 0 was drawn as the special dirt, but index 0 holds something else now.
        List<StoredStack> changed = new ArrayList<>(market.returnsOf(player.getUUID()));
        changed.add(0, StoredStack.of(new ItemStack(Items.DIAMOND, 1)));
        market.replaceReturns(player.getUUID(), changed);
        List<ItemStack> inventory = inventoryOf(player);
        TestMarket.click(menu, player, 0);
        helper.assertTrue(same(inventory, inventoryOf(player)) && market.returnsOf(player.getUUID()).size() == 4,
                "A stale click claims nothing");
        helper.assertTrue(market.returnsOf(player.getUUID()).get(0).stack().is(Items.DIAMOND), "And removes nothing");

        // An unavailable entry stays put.
        TestMarket.click(menu, player, 2);
        helper.assertTrue(market.returnsOf(player.getUUID()).size() == 4, "Unavailable entries can't be claimed");

        // Claim All with room for some. Fill the inventory so the bread doesn't fit.
        player.getInventory().clearContent();
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().setItem(7, ItemStack.EMPTY);
        TestMarket.click(menu, player, 48);
        helper.assertTrue(count(player.getInventory(), Items.DIAMOND) == 1 || count(player.getInventory(), Items.DIRT) == 3
                || count(player.getInventory(), Items.BREAD) == 5, "One free slot took the first stack that fit");
        int left = market.returnsOf(player.getUUID()).size();
        helper.assertTrue(left == 3, "One stack fit, two stacks and the unavailable one stay, got " + left);

        player.getInventory().clearContent();
        TestMarket.click(menu, player, 48);
        helper.assertTrue(market.returnsOf(player.getUUID()).size() == 1
                && !market.returnsOf(player.getUUID()).get(0).isAvailable(), "Claim All leaves only the unavailable entry");
        helper.assertTrue(count(player.getInventory(), Items.BREAD) + count(player.getInventory(), Items.DIRT) > 0,
                "The rest arrived");
        finish(helper, player);
    }

    @GameTest(template = "empty")
    public static void walletDepositAndEachWithdrawButton(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer player = player(helper);
        WalletData.get(player.server).set(player.getUUID(), 1000);
        Inventory inventory = player.getInventory();

        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = chest(helper, player);
        TestMarket.click(menu, player, WALLET);
        helper.assertTrue(nameOf(at(menu, 4)).contains(show(1000)), "The balance shows, got " + nameOf(at(menu, 4)));
        helper.assertTrue(at(menu, 19).is(Items.GOLD_INGOT) && at(menu, 20).is(Items.IRON_INGOT)
                && at(menu, 21).is(Items.COPPER_INGOT), "One button per currency item, biggest first");

        TestMarket.click(menu, player, 19);
        helper.assertTrue(count(inventory, Items.GOLD_INGOT) == 1 && wallet(player) == 936, "Click takes one gold");
        TestMarket.click(menu, player, 20, 1, ClickType.PICKUP);
        helper.assertTrue(count(inventory, Items.IRON_INGOT) == 1 && wallet(player) == 928, "Right click takes one as well");
        TestMarket.click(menu, player, 20, 0, ClickType.QUICK_MOVE);
        helper.assertTrue(count(inventory, Items.IRON_INGOT) == 1 + 64 && wallet(player) == 928 - 512,
                "Shift-click takes a full stack of silver, not a bigger coin");
        helper.assertTrue(count(inventory, Items.GOLD_INGOT) == 1, "No gold came from that");
        TestMarket.click(menu, player, 19, 0, ClickType.QUICK_MOVE);
        helper.assertTrue(count(inventory, Items.GOLD_INGOT) == 1 + 6 && wallet(player) == 416 - 384,
                "Shift-click takes what the wallet covers when it is less than a stack, got " + wallet(player));
        TestMarket.click(menu, player, 19, 0, ClickType.QUICK_MOVE);
        helper.assertTrue(wallet(player) == 32 && count(inventory, Items.GOLD_INGOT) == 7, "Too little for gold, nothing taken");

        inventory.setItem(30, named(Items.GOLD_INGOT, 5, "Not money"));
        TestMarket.click(menu, player, 38);
        helper.assertTrue(wallet(player) == 32 + 7 * 64 + 65 * 8 && count(inventory, Items.GOLD_INGOT) == 5
                && count(inventory, Items.IRON_INGOT) == 0, "Deposit All takes plain currency items and leaves renamed ones");
        helper.assertTrue(nameOf(at(menu, 4)).contains(show(wallet(player))), "The balance was redrawn");
        TestMarket.click(menu, player, MarketChestMenu.BACK_SLOT);
        helper.assertTrue(nameOf(at(menu, SORT)).startsWith("Sort"), "Back returns to Browse");

        // No currency items: the balance only.
        Currencies.set(CurrencyConfig.noItems());
        TestMarket.click(menu, player, WALLET);
        helper.assertTrue(nameOf(at(menu, 4)).contains("Wallet"), "The balance shows");
        for (int slot : new int[] {19, 20, 38, 42}) {
            helper.assertTrue(at(menu, slot).isEmpty(), "No buttons without currency items, slot " + slot);
        }
        List<ItemStack> before = inventoryOf(player);
        TestMarket.click(menu, player, 38);
        TestMarket.click(menu, player, 42);
        helper.assertTrue(same(before, inventoryOf(player)) && player.containerMenu == menu, "And those slots do nothing");
        finish(helper, player);
    }

    // Through the anvil

    @GameTest(template = "empty", batch = "phase4_search", timeoutTicks = 300)
    public static void searchTextFiltersAndClearsAndTheAnvilKeepsItemsSafe(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        ServerPlayer viewer = player(helper);
        TestMarket.add(seller, named(Items.DIAMOND_SWORD, 1, "Dragon Slayer"), 40);
        TestMarket.add(seller, new ItemStack(Items.APPLE, 3), 10);
        TestMarket.add(seller, new ItemStack(Items.DIAMOND_PICKAXE), 30);
        fillWithJunk(viewer.getInventory());

        MarketChestMenu.open(viewer, 0);
        MarketChestMenu menu = chest(helper, viewer);
        TestMarket.click(menu, viewer, SEARCH, 1, ClickType.PICKUP);
        helper.assertTrue(viewer.containerMenu == menu, "Right click with nothing to clear does nothing");
        TestMarket.click(menu, viewer, SEARCH, 0, ClickType.PICKUP);
        helper.assertTrue(viewer.containerMenu == menu, "The anvil doesn't open inside the click");

        after(helper, () -> {
            TextEntryMenu text = anvil(helper, viewer);
            helper.assertTrue(at(text, 0).is(Items.PAPER) && at(text, 1).is(Items.RED_STAINED_GLASS_PANE)
                    && at(text, 2).is(Items.LIME_STAINED_GLASS_PANE), "Paper, cancel and confirm");
            helper.assertTrue(text.getCost() == 0, "The level cost stays 0");
            text.setItemName("DRAGON");
            helper.assertTrue(text.typed().equals("DRAGON"), "The typed text arrives, got " + text.typed());
            text.setItemName("x".repeat(51));
            helper.assertTrue(text.typed().equals("DRAGON"), "Text longer than an anvil allows is ignored");
            helper.assertTrue(nameOf(at(text, 2)).equals("Confirm"), "The confirm item is not renamed by the typed text");

            sweepQuietTypes(helper, viewer, text, "anvil");
            List<ItemStack> inventory = inventoryOf(viewer);
            for (ClickType type : new ClickType[] {ClickType.PICKUP, ClickType.QUICK_MOVE}) {
                for (int slot = 3; slot < text.slots.size(); slot++) {
                    TestMarket.click(text, viewer, slot, 0, type);
                    TestMarket.click(text, viewer, slot, 1, type);
                }
                TestMarket.click(text, viewer, 0, 0, type);
                TestMarket.click(text, viewer, 0, 1, type);
                TestMarket.click(text, viewer, -999, 0, type);
            }
            helper.assertTrue(viewer.containerMenu == text && same(inventory, inventoryOf(viewer))
                    && text.getCarried().isEmpty(), "Picking at the anvil moves nothing");
            helper.assertTrue(text.quickMoveStack(viewer, 0).isEmpty() && text.quickMoveStack(viewer, 2).isEmpty()
                    && text.quickMoveStack(viewer, 10).isEmpty(), "Shift-click is refused");

            TestMarket.click(text, viewer, 2);
            TestMarket.click(text, viewer, 2);
            after(helper, () -> {
                MarketChestMenu list = chest(helper, viewer);
                helper.assertTrue(same(inventory, inventoryOf(viewer)), "The anvil returned the inventory as it was");
                helper.assertTrue(count(viewer.getInventory(), Items.PAPER) == 0, "The paper never reached the inventory");
                helper.assertTrue(at(list, 0).is(Items.DIAMOND_SWORD) && at(list, 1).isEmpty(), "Only the sword matches DRAGON");
                helper.assertTrue(loreHas(at(list, SEARCH), "Looking for: DRAGON"), "The search button shows the text");

                TestMarket.click(list, viewer, SEARCH, 0, ClickType.PICKUP);
                after(helper, () -> {
                    TextEntryMenu second = anvil(helper, viewer);
                    helper.assertTrue(nameOf(at(second, 0)).equals("DRAGON"), "The paper starts with the current search");
                    second.setItemName("minecraft:diamond");
                    TestMarket.click(second, viewer, 2);
                    after(helper, () -> {
                        MarketChestMenu byId = chest(helper, viewer);
                        helper.assertTrue(at(byId, 0).is(Items.DIAMOND_PICKAXE) && at(byId, 1).is(Items.DIAMOND_SWORD)
                                && at(byId, 2).isEmpty(), "An item id matches too, and the apple doesn't");

                        TestMarket.click(byId, viewer, SEARCH, 0, ClickType.PICKUP);
                        after(helper, () -> {
                            TextEntryMenu third = anvil(helper, viewer);
                            third.setItemName("apple");
                            TestMarket.click(third, viewer, 1);
                            after(helper, () -> {
                                MarketChestMenu cancelled = chest(helper, viewer);
                                helper.assertTrue(loreHas(at(cancelled, SEARCH), "minecraft:diamond"),
                                        "Cancel keeps the old search");
                                helper.assertTrue(count(viewer.getInventory(), Items.PAPER) == 0
                                        && same(inventory, inventoryOf(viewer)), "Cancel leaves the inventory alone");
                                TestMarket.click(cancelled, viewer, SEARCH, 1, ClickType.PICKUP);
                                helper.assertTrue(at(cancelled, 0).is(Items.DIAMOND_PICKAXE) && at(cancelled, 1).is(Items.APPLE)
                                        && at(cancelled, 2).is(Items.DIAMOND_SWORD), "Right click clears the search");
                                helper.assertTrue(loreHas(at(cancelled, SEARCH), "Showing everything"),
                                        "The button says so");

                                // Escape on the anvil: close and check that nothing was handed over.
                                TestMarket.click(cancelled, viewer, SEARCH, 0, ClickType.PICKUP);
                                after(helper, () -> {
                                    TextEntryMenu last = anvil(helper, viewer);
                                    last.setItemName("whatever");
                                    viewer.closeContainer();
                                    helper.assertTrue(count(viewer.getInventory(), Items.PAPER) == 0
                                            && same(inventory, inventoryOf(viewer)),
                                            "Closing the anvil hands nothing to the player");
                                    finish(helper, seller, viewer);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "phase4_price_text", timeoutTicks = 300)
    public static void badPriceTextStaysOnTheAnvilAndGoodTextMovesOn(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer seller = player(helper);
        seller.getInventory().setItem(4, new ItemStack(Items.EMERALD, 12));
        MarketChestMenu.open(seller, 0);
        MarketChestMenu menu = chest(helper, seller);
        TestMarket.click(menu, seller, TestMarket.menuSlot(4));
        helper.assertTrue(seller.getInventory().getItem(4).getCount() == 12, "The item doesn't move when the price is asked for");

        after(helper, () -> {
            TextEntryMenu text = anvil(helper, seller);
            String[] bad = {"", "banana", "0", "12 platinum", "-4", "99999999999999999999", "9223372036854775807 gold"};
            for (String attempt : bad) {
                text.setItemName(attempt);
                TestMarket.click(text, seller, 2);
            }
            after(helper, () -> {
                helper.assertTrue(seller.containerMenu == text, "Bad text stays on the anvil");
                helper.assertTrue(MarketData.get(seller.server).listingsOf(seller.getUUID()).isEmpty(), "Nothing was listed");
                text.setItemName("3 gold 2 silver");
                TestMarket.click(text, seller, 2);
                TestMarket.click(text, seller, 2);
                after(helper, () -> {
                    MarketChestMenu confirm = chest(helper, seller);
                    helper.assertTrue(isConfirmScreen(confirm), "Good text moves on to Confirm Sell");
                    ItemStack shown = at(confirm, 22);
                    long price = 3 * 64 + 2 * 8;
                    long cut = Cut.of(price, 5);
                    helper.assertTrue(shown.is(Items.EMERALD) && shown.getCount() == 12, "The stack is shown");
                    helper.assertTrue(loreHas(shown, "Price: " + show(price)) && loreHas(shown, "Market cut: " + show(cut))
                            && loreHas(shown, "You get: " + show(price - cut)), "Price, cut and what the seller gets, got "
                                    + lore(shown));
                    helper.assertTrue(seller.getInventory().getItem(4).getCount() == 12, "Still nothing moved");

                    TestMarket.click(confirm, seller, GO_BACK);
                    helper.assertTrue(MarketData.get(seller.server).listingsOf(seller.getUUID()).isEmpty()
                            && seller.getInventory().getItem(4).getCount() == 12, "Going back lists nothing");
                    finish(helper, seller);
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "phase4_cancel_text", timeoutTicks = 300)
    public static void cancellingThePriceTextGoesBackToTheList(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer seller = player(helper);
        seller.getInventory().setItem(10, new ItemStack(Items.EMERALD, 12));
        MarketChestMenu.open(seller, 0);
        MarketChestMenu menu = chest(helper, seller);
        TestMarket.click(menu, seller, TestMarket.menuSlot(10));
        TestMarket.click(menu, seller, SORT);
        helper.assertTrue(seller.containerMenu == menu && nameOf(at(menu, SORT)).equals("Sort: Newest"),
                "The screen waits for the next tick and ignores clicks meanwhile");
        after(helper, () -> {
            TextEntryMenu text = anvil(helper, seller);
            TestMarket.click(text, seller, 1);
            after(helper, () -> {
                MarketChestMenu back = chest(helper, seller);
                helper.assertTrue(nameOf(at(back, SORT)).startsWith("Sort"), "Back on Browse");
                helper.assertTrue(seller.getInventory().getItem(10).getCount() == 12
                        && count(seller.getInventory(), Items.PAPER) == 0, "Nothing moved, no paper");
                finish(helper, seller);
            });
        });
    }

    @GameTest(template = "empty", batch = "phase4_sale", timeoutTicks = 400)
    public static void aFullSaleThroughTheScreensWithTwoPlayers(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer sellerPlayer = player(helper);
        ServerPlayer buyerPlayer = player(helper);
        MarketData market = MarketData.get(sellerPlayer.server);
        ItemStack sword = fancySword(helper);
        sellerPlayer.getInventory().setItem(3, sword.copy());
        WalletData.get(sellerPlayer.server).set(buyerPlayer.getUUID(), 1000);
        long price = 3 * 64 + 2 * 8;
        long cut = Cut.of(price, 5);
        long poolBefore = market.pool();

        MarketChestMenu.open(sellerPlayer, 0);
        TestMarket.click(chest(helper, sellerPlayer), sellerPlayer, TestMarket.menuSlot(3));
        after(helper, () -> {
            TextEntryMenu text = anvil(helper, sellerPlayer);
            helper.assertTrue(ItemStack.matches(sellerPlayer.getInventory().getItem(3), sword), "The sword is still in the inventory");
            text.setItemName("3 gold 2 silver");
            TestMarket.click(text, sellerPlayer, 2);
            after(helper, () -> {
                MarketChestMenu confirm = chest(helper, sellerPlayer);
                helper.assertTrue(isConfirmScreen(confirm) && at(confirm, 22).is(Items.DIAMOND_SWORD), "Confirm Sell is up");
                helper.assertTrue(market.listingsOf(sellerPlayer.getUUID()).isEmpty(), "Nothing is listed before the confirm");
                TestMarket.click(confirm, sellerPlayer, CONFIRM);

                List<Listing> listed = market.listingsOf(sellerPlayer.getUUID());
                helper.assertTrue(listed.size() == 1 && listed.get(0).price() == price, "Listed at 3 gold 2 silver, got " + listed);
                helper.assertTrue(ItemStack.matches(listed.get(0).item().stack(), sword), "The listing has the sword with its data");
                helper.assertTrue(sellerPlayer.getInventory().getItem(3).isEmpty(), "The sword left the inventory");
                helper.assertTrue(!isConfirmScreen(confirm) && at(confirm, 0).is(Items.DIAMOND_SWORD), "Back on the list");

                // The buyer.
                MarketChestMenu.open(buyerPlayer, 0);
                MarketChestMenu buying = chest(helper, buyerPlayer);
                ItemStack shown = at(buying, 0);
                helper.assertTrue(shown.is(Items.DIAMOND_SWORD) && loreHas(shown, "Price: " + show(price))
                        && loreHas(shown, "Seller: " + sellerPlayer.getGameProfile().getName()), "The buyer sees the listing");
                TestMarket.click(buying, buyerPlayer, 0);
                helper.assertTrue(isConfirmScreen(buying) && at(buying, 22).is(Items.DIAMOND_SWORD), "Confirm Buy is up");
                helper.assertTrue(wallet(buyerPlayer) == 1000, "Nothing is paid before the confirm");
                TestMarket.click(buying, buyerPlayer, CONFIRM);

                helper.assertTrue(ItemStack.matches(buyerPlayer.getInventory().getItem(0), sword),
                        "The buyer has the sword with its name and enchant");
                helper.assertTrue(wallet(buyerPlayer) == 1000 - price, "The buyer paid the price, got " + wallet(buyerPlayer));
                helper.assertTrue(wallet(sellerPlayer) == price - cut, "The seller got the price minus the cut, got "
                        + wallet(sellerPlayer));
                helper.assertTrue(market.pool() == poolBefore + cut, "The pool gained the cut");
                helper.assertTrue(market.listing(listed.get(0).id()) == null, "The listing is gone");
                helper.assertTrue(at(buying, 0).isEmpty() && !isConfirmScreen(buying), "The buyer is back on an empty list");
                helper.assertTrue(market.sales().recent().get(market.sales().recent().size() - 1).price() == price,
                        "The sale is recorded");
                finish(helper, sellerPlayer, buyerPlayer);
            });
        });
    }

    @GameTest(template = "empty", batch = "phase4_sell_changed", timeoutTicks = 400)
    public static void confirmSellWhereTheStackMovedOrChangedListsNothing(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer seller = player(helper);
        runChangeCase(helper, seller, 0);
    }

    /** Each case starts a fresh sale of 12 emeralds in slot 6, then changes things before Confirm. */
    private static void runChangeCase(GameTestHelper helper, ServerPlayer seller, int which) {
        if (which == 4) {
            finish(helper, seller);
            return;
        }
        Inventory inventory = seller.getInventory();
        inventory.clearContent();
        inventory.setItem(6, new ItemStack(Items.EMERALD, 12));
        MarketChestMenu.open(seller, 0);
        TestMarket.click(chest(helper, seller), seller, TestMarket.menuSlot(6));
        after(helper, () -> {
            TextEntryMenu text = anvil(helper, seller);
            text.setItemName("100");
            TestMarket.click(text, seller, 2);
            after(helper, () -> {
                MarketChestMenu confirm = chest(helper, seller);
                helper.assertTrue(isConfirmScreen(confirm), "Confirm Sell is up for case " + which);
                ItemStack expectedAfter;
                int slotAfter;
                switch (which) {
                    case 0 -> {
                        inventory.getItem(6).shrink(5);
                        expectedAfter = new ItemStack(Items.EMERALD, 7);
                        slotAfter = 6;
                    }
                    case 1 -> {
                        inventory.setItem(6, ItemStack.EMPTY);
                        inventory.setItem(7, new ItemStack(Items.EMERALD, 12));
                        expectedAfter = new ItemStack(Items.EMERALD, 12);
                        slotAfter = 7;
                    }
                    case 2 -> {
                        inventory.setItem(6, new ItemStack(Items.DIAMOND, 12));
                        expectedAfter = new ItemStack(Items.DIAMOND, 12);
                        slotAfter = 6;
                    }
                    default -> {
                        inventory.setItem(6, named(Items.EMERALD, 12, "Renamed"));
                        expectedAfter = named(Items.EMERALD, 12, "Renamed");
                        slotAfter = 6;
                    }
                }
                TestMarket.click(confirm, seller, CONFIRM);
                helper.assertTrue(MarketData.get(seller.server).listingsOf(seller.getUUID()).isEmpty(),
                        "Nothing was listed in case " + which);
                helper.assertTrue(ItemStack.matches(inventory.getItem(slotAfter), expectedAfter),
                        "The stack is as the player left it in case " + which);
                helper.assertTrue(!isConfirmScreen(confirm), "Back on the list in case " + which);
                runChangeCase(helper, seller, which + 1);
            });
        });
    }

    @GameTest(template = "empty", batch = "phase4_wallet_amount", timeoutTicks = 300)
    public static void walletAmountButtonWithdrawsWhatWasTyped(GameTestHelper helper) {
        setup(helper, coins());
        ServerPlayer player = player(helper);
        WalletData.get(player.server).set(player.getUUID(), 500);
        MarketChestMenu.open(player, 0);
        MarketChestMenu menu = chest(helper, player);
        TestMarket.click(menu, player, WALLET);
        TestMarket.click(menu, player, 42);
        after(helper, () -> {
            TextEntryMenu text = anvil(helper, player);
            text.setItemName("later");
            TestMarket.click(text, player, 2);
            text.setItemName("1 gold 1 silver");
            TestMarket.click(text, player, 2);
            after(helper, () -> {
                MarketChestMenu wallet = chest(helper, player);
                helper.assertTrue(count(player.getInventory(), Items.GOLD_INGOT) == 1
                        && count(player.getInventory(), Items.IRON_INGOT) == 1 && wallet(player) == 500 - 72,
                        "1 gold 1 silver came out, wallet " + wallet(player));
                helper.assertTrue(nameOf(at(wallet, 4)).contains(show(500 - 72)), "The wallet page shows the new balance");
                TestMarket.click(wallet, player, 42);
                after(helper, () -> {
                    TextEntryMenu again = anvil(helper, player);
                    again.setItemName("999999");
                    TestMarket.click(again, player, 2);
                    after(helper, () -> {
                        helper.assertTrue(wallet(player) == 428 && chest(helper, player) != null,
                                "More than the wallet holds takes nothing");
                        finish(helper, player);
                    });
                });
            });
        });
    }

    // Expiry

    @GameTest(template = "empty", batch = "phase4_expiry", timeoutTicks = 1600)
    public static void aListingPastItsTimeReachesReturnsThroughTheServerTick(GameTestHelper helper) {
        setup(helper, CurrencyParser.defaultConfig());
        ServerPlayer seller = player(helper);
        MarketData market = MarketData.get(seller.server);
        long id = market.nextId();
        market.addListing(new Listing(id, seller.getUUID(), seller.getGameProfile().getName(),
                StoredStack.of(fancySword(helper)), 10, 1L));
        UUID sellerId = seller.getUUID();
        helper.succeedWhen(() -> {
            helper.assertTrue(market.listing(id) == null, "The listing should have expired");
            helper.assertTrue(market.returnsOf(sellerId).size() == 1
                    && ItemStack.matches(market.returnsOf(sellerId).get(0).stack(), fancySword(helper)),
                    "The sword should be in Returns");
            TestMarket.clearAll(seller.server);
            market.replaceReturns(sellerId, List.of());
        });
    }
}
