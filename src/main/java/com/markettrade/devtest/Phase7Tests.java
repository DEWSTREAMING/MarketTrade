package com.markettrade.devtest;

import java.util.List;
import java.util.UUID;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.market.Actor;
import com.markettrade.market.AuditEntry;
import com.markettrade.market.AuditFiles;
import com.markettrade.market.AuditLog;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;
import com.markettrade.market.Pagination;
import com.markettrade.market.PoolEntry;
import com.markettrade.market.Report;
import com.markettrade.market.Sale;
import com.markettrade.market.ShopBan;
import com.markettrade.market.StoredStack;
import com.markettrade.market.TopSellers;
import com.markettrade.market.WalletData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pool payouts, adjustments, top sellers and the version 3 saved data. Each test has a batch of its own because the market and
 * the audit folder are shared, and every test puts the pool back as it found it.
 */
@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase7Tests {
    private static final Actor CONSOLE = new Actor(null, "Console");

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        Currencies.set(CurrencyParser.defaultConfig());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        return player;
    }

    private static List<AuditEntry> audit(MinecraftServer server, UUID involved, String event) {
        return AuditFiles.read(AuditLog.dir(server), e -> e.event().equals(event) && e.involves(involved), 0, 500, w -> { })
                .entries();
    }

    @GameTest(template = "empty", batch = "p7_pay")
    public static void payingFromThePoolMovesMoneyForOnlineAndOfflinePlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MarketData market = MarketData.get(server);
        WalletData wallets = WalletData.get(server);
        long oldPool = market.pool();
        ServerPlayer online = player(helper);
        UUID offline = UUID.randomUUID();
        market.setPool(1000);

        MarketActions.PoolResult first = MarketActions.payFromPool(server, CONSOLE, online.getUUID(), "Online", 300);
        helper.assertTrue(first.status() == MarketActions.PoolStatus.OK && first.pool() == 700 && first.balance() == 300,
                "Paying an online player works");
        helper.assertTrue(market.pool() == 700 && wallets.balance(online.getUUID()) == 300, "Pool down, wallet up");
        PoolEntry entry = market.ledgerNewestFirst().get(0);
        helper.assertTrue(entry.kind() == PoolEntry.Kind.PAYOUT && entry.amount() == 300 && entry.poolAfter() == 700
                && entry.actor() == null && entry.actorName().equals("Console") && entry.target().equals(online.getUUID())
                && entry.targetName().equals("Online"), "The ledger entry is right");
        List<AuditEntry> lines = audit(server, online.getUUID(), "POOL_PAYOUT");
        helper.assertTrue(lines.size() == 1 && lines.get(0).price() == 300 && lines.get(0).actorName().equals("Console"),
                "One POOL_PAYOUT line");

        MarketActions.PoolResult second = MarketActions.payFromPool(server, CONSOLE, offline, "Offline", 700);
        helper.assertTrue(second.status() == MarketActions.PoolStatus.OK && market.pool() == 0
                && wallets.balance(offline) == 700, "Paying an offline player works, and the whole pool can go");
        helper.assertTrue(audit(server, offline, "POOL_PAYOUT").size() == 1, "One line for the offline player");
        helper.assertTrue(market.ledgerNewestFirst().get(1).poolAfter() == 700, "The ledger is newest first");

        market.setPool(oldPool);
        wallets.set(online.getUUID(), 0);
        wallets.set(offline, 0);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p7_refuse")
    public static void refusedPayoutsChangeNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MarketData market = MarketData.get(server);
        WalletData wallets = WalletData.get(server);
        long oldPool = market.pool();
        ServerPlayer one = player(helper);
        ServerPlayer two = player(helper);
        UUID full = UUID.randomUUID();
        market.setPool(500);
        wallets.set(full, Long.MAX_VALUE - 5);
        int ledgerBefore = market.ledgerNewestFirst().size();

        helper.assertTrue(MarketActions.payFromPool(server, CONSOLE, one.getUUID(), "One", 0).status()
                == MarketActions.PoolStatus.BAD_AMOUNT, "Zero is refused");
        helper.assertTrue(MarketActions.payFromPool(server, CONSOLE, one.getUUID(), "One", -4).status()
                == MarketActions.PoolStatus.BAD_AMOUNT, "A negative amount is refused");
        helper.assertTrue(MarketActions.payFromPool(server, CONSOLE, one.getUUID(), "One", 501).status()
                == MarketActions.PoolStatus.NOT_ENOUGH, "More than the pool holds is refused");
        helper.assertTrue(MarketActions.payFromPool(server, CONSOLE, full, "Full", 10).status()
                == MarketActions.PoolStatus.WALLET_FULL, "A wallet that would overflow is refused");
        helper.assertTrue(market.pool() == 500 && wallets.balance(one.getUUID()) == 0
                && wallets.balance(full) == Long.MAX_VALUE - 5, "Nothing moved");
        helper.assertTrue(market.ledgerNewestFirst().size() == ledgerBefore, "No ledger entry");
        helper.assertTrue(audit(server, full, "POOL_PAYOUT").isEmpty() && audit(server, one.getUUID(), "POOL_PAYOUT").isEmpty(),
                "No audit line");

        // Two matching players: the command says so and pays no one.
        var source = server.createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(helper.absoluteVec(new Vec3(1, 1, 1)));
        server.getCommands().performPrefixedCommand(source, "market admin pool pay @a[distance=..500] 5");
        helper.assertTrue(market.pool() == 500 && wallets.balance(one.getUUID()) == 0 && wallets.balance(two.getUUID()) == 0,
                "Two matching players pay no one");
        helper.assertTrue(market.ledgerNewestFirst().size() == ledgerBefore, "The command wrote no ledger entry");

        market.setPool(oldPool);
        wallets.set(full, 0);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p7_set")
    public static void settingThePoolWritesALedgerEntryAndAnAuditLine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MarketData market = MarketData.get(server);
        long oldPool = market.pool();
        market.setPool(40);
        String note = "fix " + UUID.randomUUID();

        MarketActions.PoolResult result = MarketActions.adjustPool(server, new Actor(UUID.randomUUID(), "Boss"), 90, note);
        helper.assertTrue(result.status() == MarketActions.PoolStatus.OK && market.pool() == 90, "The pool is set");
        PoolEntry entry = market.ledgerNewestFirst().get(0);
        helper.assertTrue(entry.kind() == PoolEntry.Kind.ADJUST && entry.amount() == 90 && entry.poolAfter() == 90
                && entry.target() == null && entry.note().equals(note) && entry.actorName().equals("Boss"),
                "The ledger entry is right");
        boolean found = AuditFiles.read(AuditLog.dir(server),
                e -> e.event().equals("POOL_ADJUST") && e.reason() != null && e.reason().contains(note), 0, 50, w -> { })
                .entries().size() == 1;
        helper.assertTrue(found, "One POOL_ADJUST line with the note");
        helper.assertTrue(MarketActions.adjustPool(server, CONSOLE, -1, "").status() == MarketActions.PoolStatus.BAD_AMOUNT
                && market.pool() == 90, "A negative pool is refused");

        market.setPool(oldPool);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p7_saved")
    public static void versionTwoDataLoadsAsVersionThreeWithNothingLost(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        MarketData source = new MarketData();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        source.addToPool(77);
        source.addListing(new Listing(source.nextId(), seller, "Seller", StoredStack.of(new ItemStack(Items.STICK, 3)), 50,
                System.currentTimeMillis() + 100000));
        source.addReturn(buyer, StoredStack.of(new ItemStack(Items.APPLE, 2)));
        source.recordSale(new Sale(5, seller, "Seller", buyer, "Buyer", "minecraft:stick", 1, 10, 1));
        source.addReport(new Report(source.nextReportId(), 3, buyer, "Buyer", seller, "Seller", 1,
                StoredStack.of(new ItemStack(Items.STICK)), "why", true));
        source.addBan(new ShopBan(buyer, "Buyer", null, "Console", "bad", 4));
        CompoundTag v2 = source.save(new CompoundTag(), registries);
        v2.putInt("version", 2);
        v2.remove("ledger");

        MarketData loaded = MarketData.load(v2, registries);
        helper.assertTrue(loaded.pool() == 77 && loaded.listingsNewestFirst().size() == 1
                && loaded.listingsNewestFirst().get(0).item().stack().getCount() == 3, "Pool and listings kept");
        helper.assertTrue(loaded.returnsOf(buyer).size() == 1 && loaded.sales().recent().size() == 1
                && loaded.sales().lifetime(seller) == 10, "Returns and sales kept");
        helper.assertTrue(loaded.reportsNewestFirst().size() == 1 && loaded.isBanned(buyer), "Reports and bans kept");
        helper.assertTrue(loaded.ledgerNewestFirst().isEmpty(), "The ledger starts empty");

        loaded.addLedger(new PoolEntry(9, PoolEntry.Kind.PAYOUT, 5, 72, UUID.randomUUID(), "A", buyer, "Buyer", ""));
        loaded.addLedger(new PoolEntry(10, PoolEntry.Kind.ADJUST, 8, 8, null, "Console", null, "", "note"));
        CompoundTag saved = loaded.save(new CompoundTag(), registries);
        helper.assertTrue(saved.getInt("version") == 3, "It saves as version 3");
        MarketData again = MarketData.load(saved, registries);
        List<PoolEntry> ledger = again.ledgerNewestFirst();
        helper.assertTrue(ledger.size() == 2 && ledger.get(0).kind() == PoolEntry.Kind.ADJUST && ledger.get(0).actor() == null
                && ledger.get(0).note().equals("note") && ledger.get(1).target().equals(buyer), "The ledger reloads");

        CompoundTag v1 = v2.copy();
        v1.putInt("version", 1);
        v1.remove("reports");
        v1.remove("bans");
        v1.remove("nextReportId");
        MarketData fromV1 = MarketData.load(v1, registries);
        helper.assertTrue(fromV1.pool() == 77 && fromV1.ledgerNewestFirst().isEmpty(), "Version 1 still loads");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "p7_top")
    public static void topSellersAreOrderedAndPaged(GameTestHelper helper) {
        MarketData data = new MarketData();
        UUID low = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        data.recordSale(new Sale(1, low, "Low", buyer, "B", "minecraft:stick", 1, 10, 1));
        data.recordSale(new Sale(2, high, "High", buyer, "B", "minecraft:stick", 1, 900, 45));
        data.recordSale(new Sale(3, mid, "MidOld", buyer, "B", "minecraft:stick", 1, 100, 5));
        data.recordSale(new Sale(4, mid, "MidNew", buyer, "B", "minecraft:stick", 1, 200, 10));

        List<TopSellers.Row> rows = TopSellers.rank(data.sales().lifetimeTotals(), data.sales().recent(), id -> null);
        helper.assertTrue(rows.size() == 3 && rows.get(0).seller().equals(high) && rows.get(1).seller().equals(mid)
                && rows.get(2).seller().equals(low), "Highest first");
        helper.assertTrue(rows.get(1).total() == 300 && rows.get(1).name().equals("MidNew"),
                "Totals add up and the newest name wins");
        int pages = Pagination.totalPages(rows.size(), 2);
        int second = Pagination.clampPage(1, pages);
        List<TopSellers.Row> page = rows.subList(Pagination.startIndex(second, 2), Pagination.endIndex(second, 2, rows.size()));
        helper.assertTrue(pages == 2 && page.size() == 1 && page.get(0).seller().equals(low), "Paging splits three sellers");
        helper.assertTrue(TopSellers.cutSince(data.sales().recent(), 3) == 15, "The week's intake counts the cuts");

        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(MarketActions.topSellers(server) != null, "The server ranking runs");
        helper.succeed();
    }
}
