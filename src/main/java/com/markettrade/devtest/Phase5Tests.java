package com.markettrade.devtest;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.gui.MarketChestMenu;
import com.markettrade.gui.TextEntryMenu;
import com.markettrade.market.Actor;
import com.markettrade.market.AuditEntry;
import com.markettrade.market.AuditFiles;
import com.markettrade.market.AuditItem;
import com.markettrade.market.AuditLog;
import com.markettrade.market.AuditSearch;
import com.markettrade.market.ItemTexts;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.Report;
import com.markettrade.market.StoredStack;
import com.markettrade.market.WalletActions;
import com.markettrade.market.WalletData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Audit log, blocked text, reports and shop bans. Every test has a batch of its own because all tests share one market and one
 * audit folder, and batches run one after the other. Mock players have random UUIDs, so each test reads only its own entries.
 */
@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase5Tests {
    private static final long NOW = 1_000_000L;
    private static final String WORD = "blockedexample";

    // Setup

    private static void config(int cooldownSeconds) {
        Currencies.set(CurrencyParser.defaultConfig());
        MarketConfigs.set(new MarketConfig(5, 7, 20, List.of(WORD), cooldownSeconds));
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        MarketData.get(player.server).replaceReturns(player.getUUID(), List.of());
        return player;
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        MarketData market = MarketData.get(helper.getLevel().getServer());
        for (ServerPlayer player : players) {
            player.closeContainer();
            market.removeBan(player.getUUID());
            market.replaceReturns(player.getUUID(), List.of());
            WalletData.get(player.server).set(player.getUUID(), 0);
        }
        TestMarket.clearAll(helper.getLevel().getServer());
        MarketConfigs.set(MarketConfig.defaults());
        helper.succeed();
    }

    private static ItemStack named(net.minecraft.world.item.Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    /** Entries that involve this player, newest first. */
    private static List<AuditEntry> entries(MinecraftServer server, UUID player) {
        return AuditFiles.read(AuditLog.dir(server), e -> e.involves(player), 0, 500, w -> { }).entries();
    }

    private static List<AuditEntry> ofEvent(MinecraftServer server, UUID player, String event) {
        return entries(server, player).stream().filter(e -> e.event().equals(event)).toList();
    }

    private static void expectOne(GameTestHelper helper, MinecraftServer server, UUID player, String event) {
        helper.assertTrue(ofEvent(server, player, event).size() == 1,
                event + " should have written exactly one line, found " + ofEvent(server, player, event).size());
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Audit events

    @GameTest(template = "empty", batch = "p5_events")
    public static void eachEventWritesOneCorrectLine(GameTestHelper helper) {
        config(0);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        ServerPlayer reporter = player(helper);
        MarketData market = MarketData.get(server);
        WalletData.get(server).set(buyer.getUUID(), 1000);

        // LIST, then CANCEL
        seller.getInventory().setItem(0, named(Items.STICK, 3, "Audit \"Stick\"\nline two"));
        ItemStack expected = seller.getInventory().getItem(0).copy();
        MarketActions.SellResult listed = MarketActions.sell(seller, 0, expected, 40, NOW);
        helper.assertTrue(listed.status() == MarketActions.SellStatus.OK, "Listing works");
        List<AuditEntry> lists = ofEvent(server, seller.getUUID(), "LIST");
        helper.assertTrue(lists.size() == 1, "LIST written once");
        AuditEntry list = lists.get(0);
        helper.assertTrue(list.listing() == listed.id() && list.price() == 40, "LIST has the listing and the price");
        helper.assertTrue(list.item().count() == 3 && list.item().id().equals("minecraft:stick"), "LIST has the item");
        helper.assertTrue(list.item().name().equals("Audit \"Stick\"\nline two"), "The name survives quotes and a newline");
        helper.assertTrue(list.item().texts().contains("Audit \"Stick\"\nline two"), "The text is kept");
        helper.assertTrue(list.item().data().contains("minecraft:stick"), "The SNBT is kept");
        helper.assertTrue(list.actorName().equals(seller.getGameProfile().getName()), "The name at that moment is kept");
        MarketActions.cancel(seller, listed.id());
        expectOne(helper, server, seller.getUUID(), "CANCEL");

        // BUY
        seller.getInventory().setItem(0, new ItemStack(Items.APPLE, 2));
        long id = MarketActions.sell(seller, 0, seller.getInventory().getItem(0).copy(), 100, NOW).id();
        MarketActions.buy(buyer, id, 100, NOW);
        AuditEntry buy = ofEvent(server, buyer.getUUID(), "BUY").get(0);
        helper.assertTrue(buy.actor().equals(buyer.getUUID()) && buy.target().equals(seller.getUUID()), "BUY actor and target");
        helper.assertTrue(buy.price() == 100 && buy.cut() == 5 && buy.listing() == id, "BUY price, cut and listing");
        expectOne(helper, server, seller.getUUID(), "BUY");

        // EXPIRE
        long old = TestMarket.add(seller, new ItemStack(Items.COAL, 4), 10, -1);
        MarketActions.expireDue(server, System.currentTimeMillis());
        helper.assertTrue(market.listing(old) == null, "The old listing expired");
        AuditEntry expire = ofEvent(server, seller.getUUID(), "EXPIRE").get(0);
        helper.assertTrue(expire.listing() == old && expire.actor() == null, "EXPIRE has no actor");

        // ADMIN_REMOVE and ADMIN_WALLET
        long removable = TestMarket.add(seller, new ItemStack(Items.COAL, 1), 10);
        MarketActions.adminRemove(server, new Actor(null, "Console"), removable);
        AuditEntry removed = ofEvent(server, seller.getUUID(), "ADMIN_REMOVE").get(0);
        helper.assertTrue(removed.actorName().equals("Console") && removed.listing() == removable, "ADMIN_REMOVE");
        WalletActions.adminGive(server, new Actor(null, "Console"), seller.getUUID(), "Seller", 25);
        AuditEntry wallet = ofEvent(server, seller.getUUID(), "ADMIN_WALLET").get(0);
        helper.assertTrue(wallet.price() == 25 && wallet.reason().startsWith("give"), "ADMIN_WALLET");

        // REPORT, REPORT_CLOSE
        long reported = TestMarket.add(seller, new ItemStack(Items.IRON_INGOT, 2), 10);
        MarketActions.ReportResult report = MarketActions.report(reporter, reported, "looks odd", NOW);
        helper.assertTrue(report.status() == MarketActions.ReportStatus.OK, "Report works");
        AuditEntry reportLine = ofEvent(server, reporter.getUUID(), "REPORT").get(0);
        helper.assertTrue(reportLine.listing() == reported && reportLine.reason().contains("looks odd"), "REPORT");
        MarketActions.closeReport(server, new Actor(null, "Console"), report.id());
        expectOne(helper, server, seller.getUUID(), "REPORT_CLOSE");

        // SHOP_BAN, SHOP_UNBAN
        MarketActions.shopBan(server, new Actor(null, "Console"), reporter.getUUID(), "Reporter", "test");
        expectOne(helper, server, reporter.getUUID(), "SHOP_BAN");
        MarketActions.shopUnban(server, new Actor(null, "Console"), reporter.getUUID(), "Reporter");
        expectOne(helper, server, reporter.getUUID(), "SHOP_UNBAN");

        // RENAME and LIST_BLOCKED
        ItemStack renamed = named(Items.DIAMOND_SWORD, 1, "Fresh Name");
        MarketActions.logRename(seller, new ItemStack(Items.DIAMOND_SWORD), renamed);
        AuditEntry rename = ofEvent(server, seller.getUUID(), "RENAME").get(0);
        helper.assertTrue(rename.item().name().equals("Fresh Name"), "RENAME has the new name");
        seller.getInventory().setItem(1, named(Items.STICK, 1, "x " + WORD));
        MarketActions.sell(seller, 1, seller.getInventory().getItem(1).copy(), 5, NOW);
        expectOne(helper, server, seller.getUUID(), "LIST_BLOCKED");
        finish(helper, seller, buyer, reporter);
    }

    // Anvil

    @GameTest(template = "empty", batch = "p5_audit_item")
    public static void readingAnItemForTheAuditNeverThrows(GameTestHelper helper) {
        // No server means saving the stack fails, which stands in for a mod's item that can't be read.
        AuditItem item = AuditLog.item(null, StoredStack.of(new ItemStack(Items.DIAMOND_PICKAXE)));
        helper.assertTrue("minecraft:diamond_pickaxe".equals(item.id()), "The id is kept");
        helper.assertTrue(item.count() == 1, "The count is kept");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p5_anvil_event")
    public static void anAnvilRepairEventLogsARenameOnlyWhenTheNameChanged(GameTestHelper helper) {
        config(0);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = player(helper);
        ItemStack left = new ItemStack(Items.IRON_SWORD);
        ItemStack output = named(Items.IRON_SWORD, 1, "Renamed Blade");
        NeoForge.EVENT_BUS.post(new AnvilRepairEvent(player, left, ItemStack.EMPTY, output));
        helper.assertTrue(ofEvent(server, player.getUUID(), "RENAME").size() == 1, "The rename is logged");

        // Same name in and out: a repair, not a rename.
        NeoForge.EVENT_BUS.post(new AnvilRepairEvent(player, output.copy(), new ItemStack(Items.IRON_INGOT), output.copy()));
        helper.assertTrue(ofEvent(server, player.getUUID(), "RENAME").size() == 1, "A repair with the same name logs nothing");
        finish(helper, player);
    }

    @GameTest(template = "empty", batch = "p5_text_entry", timeoutTicks = 200)
    public static void theTextEntryAnvilNeverLogsARename(GameTestHelper helper) {
        config(0);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = player(helper);
        MarketChestMenu.open(player, 0);
        TestMarket.click(player.containerMenu, player, MarketChestMenu.SEARCH_SLOT);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.containerMenu instanceof TextEntryMenu, "The text screen should be open");
            TextEntryMenu text = (TextEntryMenu) player.containerMenu;
            text.setItemName("some search");
            TestMarket.click(text, player, 2);
            TestMarket.click(text, player, 2, 0, ClickType.QUICK_MOVE);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(ofEvent(server, player.getUUID(), "RENAME").isEmpty(), "No rename was logged");
                finish(helper, player);
            });
        });
    }

    // Finding text

    @GameTest(template = "empty", batch = "p5_texts")
    public static void textInsideShulkerBoxesAndBooksIsFound(GameTestHelper helper) {
        config(0);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(named(Items.STICK, 1, "deep " + WORD))));
        helper.assertTrue(ItemTexts.collect(box).contains("deep " + WORD), "Text in a shulker box is found");

        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        written.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("A Title"), "An Author", 0,
                List.of(Filterable.passThrough(Component.literal("page one " + WORD))), true));
        List<String> writtenTexts = ItemTexts.collect(written);
        helper.assertTrue(writtenTexts.contains("A Title") && writtenTexts.contains("An Author")
                && writtenTexts.contains("page one " + WORD), "Title, author and pages of a written book are found");

        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK);
        writable.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough("draft " + WORD))));
        helper.assertTrue(ItemTexts.collect(writable).contains("draft " + WORD), "A writable book page is found");

        ItemStack lore = new ItemStack(Items.STICK);
        lore.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("lore " + WORD))));
        helper.assertTrue(ItemTexts.collect(lore).contains("lore " + WORD), "Lore is found");

        // A box inside a box inside a box still counts, up to the depth limit.
        ItemStack nested = named(Items.STICK, 1, "bottom");
        for (int i = 0; i < 3; i++) {
            ItemStack outer = new ItemStack(Items.SHULKER_BOX);
            outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(nested)));
            nested = outer;
        }
        helper.assertTrue(ItemTexts.collect(nested).contains("bottom"), "Nested containers are searched");

        ServerPlayer player = player(helper);
        for (ItemStack stack : List.of(box, written, writable, lore)) {
            player.getInventory().setItem(0, stack.copy());
            MarketActions.SellResult result = MarketActions.sell(player, 0, stack.copy(), 10, NOW);
            helper.assertTrue(result.status() == MarketActions.SellStatus.BLOCKED_TEXT, "The blocked word check sees it in "
                    + stack.getItem());
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), stack), "The item stays in the inventory");
        }
        helper.assertTrue(MarketData.get(player.server).countListingsOf(player.getUUID()) == 0, "Nothing was listed");
        finish(helper, player);
    }

    @GameTest(template = "empty", batch = "p5_blocked")
    public static void aBlockedListingLeavesTheItemAndANormalOneStillWorks(GameTestHelper helper) {
        config(0);
        ServerPlayer player = player(helper);
        ItemStack bad = named(Items.STICK, 5, "§cB-L-O-C-K-E-D example");
        player.getInventory().setItem(0, bad.copy());
        helper.assertTrue(MarketActions.sell(player, 0, bad.copy(), 10, NOW).status() == MarketActions.SellStatus.BLOCKED_TEXT,
                "Formatting codes and punctuation do not hide the word");
        helper.assertTrue(count(player, Items.STICK) == 5, "The sticks are still there");
        player.getInventory().setItem(0, named(Items.STICK, 5, "Nice stick"));
        helper.assertTrue(MarketActions.sell(player, 0, player.getInventory().getItem(0).copy(), 10, NOW).status()
                == MarketActions.SellStatus.OK, "A clean name lists fine");
        finish(helper, player);
    }

    // Renamed item chain

    @GameTest(template = "empty", batch = "p5_chain")
    public static void aRenamedItemKeepsItsNameInTheAuditAndTheReportAfterTheListingIsGone(GameTestHelper helper) {
        config(0);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer namer = player(helper);
        ServerPlayer middle = player(helper);
        ServerPlayer other = player(helper);
        MarketData market = MarketData.get(server);
        WalletData.get(server).set(middle.getUUID(), 500);

        ItemStack sword = named(Items.DIAMOND_SWORD, 1, "Marked Sword");
        MarketActions.logRename(namer, new ItemStack(Items.DIAMOND_SWORD), sword);
        namer.getInventory().setItem(0, sword.copy());
        long first = MarketActions.sell(namer, 0, sword.copy(), 50, NOW).id();
        helper.assertTrue(MarketActions.buy(middle, first, 50, NOW).status() == MarketActions.BuyStatus.OK, "Middle buys it");
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (middle.getInventory().getItem(i).is(Items.DIAMOND_SWORD)) {
                slot = i;
            }
        }
        helper.assertTrue(slot >= 0, "Middle has the sword");
        long second = MarketActions.sell(middle, slot, middle.getInventory().getItem(slot).copy(), 60, NOW).id();
        helper.assertTrue(second > 0, "Middle relists it");

        MarketActions.ReportResult report = MarketActions.report(other, second, "", NOW);
        helper.assertTrue(report.status() == MarketActions.ReportStatus.OK, "Other reports the relisting");
        MarketActions.adminRemove(server, new Actor(null, "Console"), second);
        helper.assertTrue(market.listing(second) == null, "The listing is gone");

        Report copy = market.report(report.id());
        helper.assertTrue(copy != null && copy.item().isAvailable()
                && copy.item().stack().getHoverName().getString().equals("Marked Sword"), "The report copy keeps the name");
        helper.assertTrue(ofEvent(server, namer.getUUID(), "RENAME").get(0).item().name().equals("Marked Sword"),
                "The rename is in the audit under the person who typed it");
        helper.assertTrue(ofEvent(server, middle.getUUID(), "BUY").get(0).item().name().equals("Marked Sword"),
                "The sale is in the audit with the name");
        helper.assertTrue(ofEvent(server, other.getUUID(), "REPORT").get(0).item().name().equals("Marked Sword"),
                "The report line has the name");
        finish(helper, namer, middle, other);
    }

    // Reports

    @GameTest(template = "empty", batch = "p5_report_refusals")
    public static void reportRefusals(GameTestHelper helper) {
        config(30);
        ServerPlayer seller = player(helper);
        ServerPlayer reporter = player(helper);
        long one = TestMarket.add(seller, new ItemStack(Items.STICK, 1), 10);
        long two = TestMarket.add(seller, new ItemStack(Items.COAL, 1), 10);
        long mine = TestMarket.add(reporter, new ItemStack(Items.APPLE, 1), 10);

        helper.assertTrue(MarketActions.report(reporter, mine, "", NOW).status() == MarketActions.ReportStatus.OWN_LISTING,
                "Own listing refused");
        helper.assertTrue(MarketActions.report(reporter, 987654, "", NOW).status() == MarketActions.ReportStatus.NOT_FOUND,
                "Unknown id refused");
        helper.assertTrue(MarketActions.report(reporter, one, "", NOW).status() == MarketActions.ReportStatus.OK, "First works");
        helper.assertTrue(MarketActions.report(reporter, one, "", NOW + 60_000).status()
                == MarketActions.ReportStatus.ALREADY_REPORTED, "Same listing twice refused");
        MarketActions.ReportResult wait = MarketActions.report(reporter, two, "", NOW + 1000);
        helper.assertTrue(wait.status() == MarketActions.ReportStatus.COOLDOWN && wait.waitSeconds() == 29,
                "Cooldown refused with the seconds left, got " + wait);
        helper.assertTrue(MarketActions.report(reporter, two, "", NOW + 31_000).status() == MarketActions.ReportStatus.OK,
                "Works after the cooldown");

        String longReason = "r".repeat(500);
        ServerPlayer third = player(helper);
        long three = TestMarket.add(seller, new ItemStack(Items.FLINT, 1), 10);
        long id = MarketActions.report(third, three, longReason, NOW).id();
        helper.assertTrue(MarketData.get(seller.server).report(id).reason().length() == 200, "The reason is cut to 200");
        finish(helper, seller, reporter, third);
    }

    // Bans

    @GameTest(template = "empty", batch = "p5_ban", timeoutTicks = 200)
    public static void aBanMovesListingsAndRefusesSellBuyAndReportButAllowsTheRest(GameTestHelper helper) {
        config(0);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer banned = player(helper);
        ServerPlayer other = player(helper);
        MarketData market = MarketData.get(server);
        long a = TestMarket.add(banned, new ItemStack(Items.STICK, 2), 10);
        long b = TestMarket.add(banned, new ItemStack(Items.COAL, 3), 10);
        long others = TestMarket.add(other, new ItemStack(Items.APPLE, 1), 10);
        WalletData.get(server).set(banned.getUUID(), 500);

        MarketActions.BanResult result = MarketActions.shopBan(server, new Actor(null, "Console"), banned.getUUID(), "Banned", "test");
        helper.assertTrue(result.status() == MarketActions.BanStatus.OK && result.moved() == 2, "Two listings moved");
        helper.assertTrue(market.listing(a) == null && market.listing(b) == null && market.listing(others) != null,
                "Only the banned player's listings left the market");
        helper.assertTrue(market.returnsOf(banned.getUUID()).size() == 2, "Both stacks are in Returns");
        helper.assertTrue(ofEvent(server, banned.getUUID(), "ADMIN_REMOVE").size() == 2, "Each move is logged");
        helper.assertTrue(MarketActions.shopBan(server, new Actor(null, "Console"), banned.getUUID(), "Banned", "").status()
                == MarketActions.BanStatus.ALREADY_BANNED, "A second ban is refused");

        banned.getInventory().setItem(0, new ItemStack(Items.STICK, 4));
        helper.assertTrue(MarketActions.sell(banned, 0, banned.getInventory().getItem(0).copy(), 5, NOW).status()
                == MarketActions.SellStatus.BANNED, "Sell refused");
        helper.assertTrue(MarketActions.buy(banned, others, 10, NOW).status() == MarketActions.BuyStatus.BANNED, "Buy refused");
        helper.assertTrue(MarketActions.report(banned, others, "", NOW).status() == MarketActions.ReportStatus.BANNED,
                "Report refused");

        // Through a command
        banned.server.getCommands().performPrefixedCommand(banned.createCommandSourceStack(), "market sell 5");
        helper.assertTrue(count(banned, Items.STICK) == 4 && market.countListingsOf(banned.getUUID()) == 0,
                "The sell command did nothing");

        // Through the screens: nothing opens the price screen, and Confirm Buy never shows.
        MarketChestMenu.open(banned, 0);
        MarketChestMenu menu = (MarketChestMenu) banned.containerMenu;
        TestMarket.click(menu, banned, 0);
        helper.assertTrue(menu.getSlot(45).getItem().isEmpty() || !menu.getSlot(45).getItem().is(Items.LIME_STAINED_GLASS_PANE),
                "No confirm buy screen for a banned player");
        TestMarket.click(menu, banned, TestMarket.menuSlot(0));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(banned.containerMenu == menu, "The price screen did not open");

            // Still allowed: cancel, claim, deposit, withdraw.
            long fresh = TestMarket.add(banned, new ItemStack(Items.FLINT, 1), 10);
            helper.assertTrue(MarketActions.cancel(banned, fresh).status() == MarketActions.CancelStatus.OK, "Cancel works");
            helper.assertTrue(MarketActions.claimReturns(banned).claimed() == 6, "Claiming Returns works");
            helper.assertTrue(WalletActions.withdraw(banned, 100).given() == 100, "Withdraw works");
            helper.assertTrue(WalletActions.depositAll(banned).deposited() == 100, "Deposit works");
            helper.assertTrue(WalletData.get(server).balance(banned.getUUID()) == 500, "The money is all still there");

            helper.assertTrue(MarketActions.shopUnban(server, new Actor(null, "Console"), banned.getUUID(), "Banned").status()
                    == MarketActions.BanStatus.OK, "Unban works");
            helper.assertTrue(market.listing(a) == null, "Old listings are not put back");
            helper.assertTrue(MarketActions.sell(banned, 0, banned.getInventory().getItem(0).copy(), 5, NOW).status()
                    == MarketActions.SellStatus.OK || count(banned, Items.STICK) > 0, "Selling is possible again");
            finish(helper, banned, other);
        });
    }

    // Saved data

    @GameTest(template = "empty", batch = "p5_upgrade")
    public static void versionOneMarketDataLoadsIntoVersionTwoWithNothingLost(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        CompoundTag v1 = new CompoundTag();
        v1.putInt("version", 1);
        v1.putLong("nextId", 9);
        v1.putLong("pool", 77);

        ListTag listings = new ListTag();
        CompoundTag listing = new CompoundTag();
        listing.putLong("id", 8);
        listing.putString("seller", seller.toString());
        listing.putString("sellerName", "Old Seller");
        listing.put("item", StoredStack.of(named(Items.DIAMOND, 3, "Kept")).toTag(registries));
        listing.putLong("price", 123);
        listing.putLong("expiresAt", 5_000_000L);
        listings.add(listing);
        v1.put("listings", listings);

        ListTag returns = new ListTag();
        CompoundTag returned = new CompoundTag();
        returned.putString("player", buyer.toString());
        ListTag items = new ListTag();
        items.add(StoredStack.of(new ItemStack(Items.EMERALD, 2)).toTag(registries));
        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "gonemod:thing");
        unknown.putInt("count", 4);
        items.add(unknown);
        returned.put("items", items);
        returns.add(returned);
        v1.put("returns", returns);

        ListTag sales = new ListTag();
        CompoundTag sale = new CompoundTag();
        sale.putLong("time", 42);
        sale.putString("seller", seller.toString());
        sale.putString("sellerName", "Old Seller");
        sale.putString("buyer", buyer.toString());
        sale.putString("buyerName", "Old Buyer");
        sale.putString("item", "minecraft:stick");
        sale.putInt("count", 1);
        sale.putLong("price", 10);
        sale.putLong("cut", 1);
        sales.add(sale);
        v1.put("sales", sales);
        ListTag lifetime = new ListTag();
        CompoundTag total = new CompoundTag();
        total.putString("player", seller.toString());
        total.putLong("total", 10);
        lifetime.add(total);
        v1.put("lifetime", lifetime);

        MarketData data = MarketData.load(v1, registries);
        helper.assertTrue(data.pool() == 77, "Pool kept");
        Listing loaded = data.listing(8);
        helper.assertTrue(loaded != null && loaded.price() == 123 && loaded.sellerName().equals("Old Seller")
                && loaded.item().stack().getCount() == 3
                && loaded.item().stack().getHoverName().getString().equals("Kept"), "Listing kept with its data");
        helper.assertTrue(data.returnsOf(buyer).size() == 2 && !data.returnsOf(buyer).get(1).isAvailable(),
                "Returns kept, including the unavailable stack");
        helper.assertTrue(data.sales().recent().size() == 1 && data.sales().lifetime(seller) == 10, "Sales kept");
        helper.assertTrue(data.nextId() == 9, "The id counter is kept");
        helper.assertTrue(data.openReportsNewestFirst().isEmpty() && data.bansNewestFirst().isEmpty(), "New parts start empty");
        helper.assertTrue(data.nextReportId() == 1, "Report ids start at 1");

        data.addBan(new com.markettrade.market.ShopBan(buyer, "Old Buyer", null, "Console", "why", 5));
        CompoundTag saved = data.save(new CompoundTag(), registries);
        helper.assertTrue(saved.getInt("version") == MarketData.FORMAT_VERSION, "It saves as the newest version");
        MarketData again = MarketData.load(saved, registries);
        helper.assertTrue(again.isBanned(buyer) && again.ban(buyer).bannedBy() == null, "A console ban reloads");
        helper.assertTrue(again.listing(8) != null && again.pool() == 77, "Version 2 reloads");

        CompoundTag newer = saved.copy();
        newer.putInt("version", MarketData.FORMAT_VERSION + 1);
        boolean refused = false;
        try {
            MarketData.load(newer, registries);
        } catch (IllegalStateException e) {
            refused = true;
        }
        helper.assertTrue(refused, "A newer format is refused, not guessed at");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p5_reports_saved")
    public static void openReportsAreAlwaysKeptAndClosedOnesAreCapped(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        MarketData data = new MarketData();
        UUID who = UUID.randomUUID();
        StoredStack stack = StoredStack.of(new ItemStack(Items.STICK));
        long firstOpen = data.nextReportId();
        data.addReport(new Report(firstOpen, 1, who, "R", who, "S", 1, stack, "", true));
        for (int i = 0; i < MarketData.KEEP_CLOSED_REPORTS + 20; i++) {
            long id = data.nextReportId();
            data.addReport(new Report(id, 2, who, "R", who, "S", 2 + i, stack, "", true));
            data.closeReport(id);
        }
        helper.assertTrue(data.report(firstOpen) != null && data.report(firstOpen).open(), "The old open report is kept");
        long closed = data.reportsNewestFirst().stream().filter(r -> !r.open()).count();
        helper.assertTrue(closed == MarketData.KEEP_CLOSED_REPORTS, "Closed reports are capped, found " + closed);
        MarketData again = MarketData.load(data.save(new CompoundTag(), registries), registries);
        helper.assertTrue(again.reportsNewestFirst().size() == data.reportsNewestFirst().size(), "All reload");
        helper.succeed();
    }

    // History and find

    @GameTest(template = "empty", batch = "p5_history", timeoutTicks = 200)
    public static void historyAndFindReturnTheRightEntriesAcrossTwoMonthFiles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID me = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        String tag = "zq" + Long.toHexString(me.getLeastSignificantBits());
        long jan = 1_768_435_200_000L;
        long feb = 1_771_113_600_000L;
        Path dir = AuditLog.dir(server);
        try {
            for (int i = 0; i < 7; i++) {
                AuditFiles.append(dir, new AuditEntry(jan + i, "LIST").actor(me, "Me").listing(i).item(
                        new AuditItem("minecraft:stick", 1, "Old " + tag + " " + i, List.of("Old " + tag + " " + i), "{}")));
            }
            for (int i = 0; i < 7; i++) {
                AuditFiles.append(dir, new AuditEntry(feb + i, "CANCEL").actor(me, "Me").listing(100 + i));
            }
            AuditFiles.append(dir, new AuditEntry(feb + 50, "LIST").actor(someoneElse, "Else").item(
                    new AuditItem("minecraft:stick", 1, "Other " + tag.toUpperCase(), List.of(), "{}")));
        } catch (java.io.IOException e) {
            helper.fail("Could not write test lines: " + e.getMessage());
            return;
        }

        AtomicReference<AuditFiles.Page> history1 = new AtomicReference<>();
        AtomicReference<AuditFiles.Page> history2 = new AtomicReference<>();
        AtomicReference<AuditFiles.Page> found = new AtomicReference<>();
        AuditSearch.history(server, me, 1, history1::set);
        AuditSearch.history(server, me, 2, history2::set);
        AuditSearch.find(server, tag.toUpperCase(), 1, found::set);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(history1.get() != null && history2.get() != null && found.get() != null,
                    "The searches should have answered");
            helper.assertTrue(history1.get().entries().size() == 10 && history1.get().more(), "Page 1 has 10 and more");
            helper.assertTrue(history1.get().entries().get(0).event().equals("CANCEL")
                    && history1.get().entries().get(0).listing() == 106, "Newest first, from the newer month");
            helper.assertTrue(history2.get().entries().size() == 4 && !history2.get().more(), "Page 2 has the other 4");
            helper.assertTrue(history2.get().entries().get(3).listing() == 0, "The oldest comes last");
            helper.assertTrue(history1.get().entries().stream().noneMatch(e -> someoneElse.equals(e.actor())),
                    "History holds only that player");
            helper.assertTrue(found.get().entries().size() == 8, "Find matches the name text, ignoring case, in both months and "
                    + "for any player, found " + found.get().entries().size());
            finish(helper);
        });
    }
}
