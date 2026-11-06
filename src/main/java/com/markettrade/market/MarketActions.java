package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;

import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Every change to listings, Returns, the pool and the sales goes through here, on the server thread. Commands and screens
 * call these and turn the result into text. Each action does every check that can fail before anything moves.
 */
public final class MarketActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAIN_SLOTS = 36;
    private static final int OFFHAND_SLOT = 40;

    public enum SellStatus {
        /** {@code EMPTY_HAND} also covers an empty slot. {@code CHANGED} means the slot no longer holds what was shown. */
        OK, EMPTY_HAND, CHANGED, BAD_PRICE, LIMIT, BLOCKED_TEXT, BANNED
    }

    public record SellResult(SellStatus status, long id, long expiresAt, int limit) {
    }

    public enum BuyStatus {
        OK, NOT_FOUND, UNAVAILABLE, EXPIRED, PRICE_CHANGED, OWN_LISTING, CANNOT_PAY, OVERFLOW, BANNED
    }

    /** {@code toReturns} is how many items did not fit and went to the buyer's Returns. */
    public record BuyResult(BuyStatus status, long price, long cut, ItemStack item, int toReturns) {
    }

    public enum CancelStatus {
        OK, NOT_FOUND, NOT_YOURS
    }

    public record CancelResult(CancelStatus status, ItemStack item) {
    }

    public record ClaimResult(int claimed, int stacksLeft, int unavailableLeft) {
    }

    public enum ClaimOneStatus {
        /** All of the stack arrived. */
        OK,
        /** Some arrived and the rest stays in Returns. */
        PARTIAL,
        NO_ROOM,
        /** The entry at that place is not the one that was shown. */
        GONE,
        UNAVAILABLE
    }

    /** {@code given} is how many items arrived, {@code left} how many stayed. */
    public record ClaimOneResult(ClaimOneStatus status, ItemStack item, int given, int left) {
    }

    public enum RemoveStatus {
        OK, NOT_FOUND
    }

    public enum ReportStatus {
        OK, BANNED, NOT_FOUND, OWN_LISTING, ALREADY_REPORTED, COOLDOWN
    }

    /** {@code id} is the new report, {@code waitSeconds} is what is left of the cooldown. */
    public record ReportResult(ReportStatus status, long id, long waitSeconds) {
    }

    public enum CloseStatus {
        OK, NOT_FOUND, ALREADY_CLOSED
    }

    public enum BanStatus {
        OK, ALREADY_BANNED, NOT_BANNED
    }

    /** {@code moved} is how many listings went to the player's Returns. */
    public record BanResult(BanStatus status, int moved) {
    }

    public static final int MAX_REASON_LENGTH = 200;

    public enum PoolStatus {
        OK, BAD_AMOUNT, NOT_ENOUGH, WALLET_FULL
    }

    /** {@code pool} and {@code balance} are the values after the action. For a refusal they are the current ones. */
    public record PoolResult(PoolStatus status, long pool, long balance) {
    }

    private MarketActions() {
    }

    // Listing

    /**
     * Lists the stack in {@code slot} of the main inventory. {@code expected} is the stack the player was shown. If the slot
     * holds anything else by now (a different item, data or count), nothing happens.
     */
    public static SellResult sell(ServerPlayer player, int slot, ItemStack expected, long price, long now) {
        MarketConfig config = MarketConfigs.current();
        MarketData market = MarketData.get(player.server);
        Inventory inventory = player.getInventory();
        if (market.isBanned(player.getUUID())) {
            return new SellResult(SellStatus.BANNED, 0, 0, config.maxListingsPerPlayer());
        }
        if (slot < 0 || slot >= MAIN_SLOTS) {
            return new SellResult(SellStatus.CHANGED, 0, 0, config.maxListingsPerPlayer());
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return new SellResult(SellStatus.EMPTY_HAND, 0, 0, config.maxListingsPerPlayer());
        }
        if (!ItemStack.matches(stack, expected)) {
            return new SellResult(SellStatus.CHANGED, 0, 0, config.maxListingsPerPlayer());
        }
        if (price < 1) {
            return new SellResult(SellStatus.BAD_PRICE, 0, 0, config.maxListingsPerPlayer());
        }
        if (market.countListingsOf(player.getUUID()) >= config.maxListingsPerPlayer()) {
            return new SellResult(SellStatus.LIMIT, 0, 0, config.maxListingsPerPlayer());
        }

        StoredStack stored = StoredStack.of(stack);
        String blocked = BlockedWords.find(ItemTexts.collect(stack), config.blockedWords());
        if (blocked != null) {
            AuditLog.log(player.server, new AuditEntry(now, "LIST_BLOCKED").actor(player.getUUID(), nameOf(player))
                    .price(price).reason("blocked word: " + blocked).item(AuditLog.item(player.server, stored)));
            return new SellResult(SellStatus.BLOCKED_TEXT, 0, 0, config.maxListingsPerPlayer());
        }
        long id = market.nextId();
        long expiresAt = Expiry.at(now, config.listingDays());
        market.addListing(new Listing(id, player.getUUID(), player.getGameProfile().getName(), stored, price, expiresAt));
        inventory.setItem(slot, ItemStack.EMPTY);
        AuditLog.log(player.server, new AuditEntry(now, "LIST").actor(player.getUUID(), nameOf(player))
                .target(player.getUUID(), nameOf(player)).listing(id).price(price)
                .item(AuditLog.item(player.server, stored)));
        return new SellResult(SellStatus.OK, id, expiresAt, config.maxListingsPerPlayer());
    }

    public static CancelResult cancel(ServerPlayer player, long listingId) {
        MarketData market = MarketData.get(player.server);
        Listing listing = market.listing(listingId);
        if (listing == null) {
            return new CancelResult(CancelStatus.NOT_FOUND, ItemStack.EMPTY);
        }
        if (!listing.seller().equals(player.getUUID())) {
            return new CancelResult(CancelStatus.NOT_YOURS, ItemStack.EMPTY);
        }
        market.removeListing(listingId);
        market.addReturn(listing.seller(), listing.item());
        AuditLog.log(player.server, new AuditEntry(System.currentTimeMillis(), "CANCEL")
                .actor(player.getUUID(), nameOf(player)).target(listing.seller(), listing.sellerName())
                .listing(listingId).price(listing.price()).item(AuditLog.item(player.server, listing.item())));
        return new CancelResult(CancelStatus.OK, display(listing.item()));
    }

    /** Takes a listing off the market for an admin. The item goes to the seller's Returns. */
    public static RemoveStatus adminRemove(MinecraftServer server, String actor, long listingId) {
        return adminRemove(server, Actor.named(actor), listingId);
    }

    public static RemoveStatus adminRemove(MinecraftServer server, Actor actor, long listingId) {
        MarketData market = MarketData.get(server);
        Listing listing = market.removeListing(listingId);
        if (listing == null) {
            return RemoveStatus.NOT_FOUND;
        }
        market.addReturn(listing.seller(), listing.item());
        AuditLog.log(server, new AuditEntry(System.currentTimeMillis(), "ADMIN_REMOVE").actor(actor)
                .target(listing.seller(), listing.sellerName()).listing(listingId).price(listing.price())
                .item(AuditLog.item(server, listing.item())));
        return RemoveStatus.OK;
    }

    /** Moves every listing past its time to the seller's Returns. Listings that are unavailable never expire. */
    public static int expireDue(MinecraftServer server, long now) {
        MarketData market = MarketData.get(server);
        int expired = 0;
        for (Listing listing : market.listingsNewestFirst()) {
            if (!listing.item().isAvailable() || !Expiry.isExpired(listing.expiresAt(), now)) {
                continue;
            }
            market.removeListing(listing.id());
            market.addReturn(listing.seller(), listing.item());
            expired++;
            AuditLog.log(server, new AuditEntry(now, "EXPIRE").target(listing.seller(), listing.sellerName())
                    .listing(listing.id()).price(listing.price()).item(AuditLog.item(server, listing.item())));
            ServerPlayer seller = server.getPlayerList().getPlayer(listing.seller());
            if (seller != null) {
                seller.sendSystemMessage(Text.translatable("markettrade.listing_expired", listing.id(),
                        display(listing.item()).getHoverName()));
            }
        }
        if (expired > 0) {
            LOGGER.info("{} listing(s) expired and went to Returns", expired);
        }
        return expired;
    }

    // Buying

    public static BuyResult buy(ServerPlayer buyer, long listingId, long expectedPrice, long now) {
        MinecraftServer server = buyer.server;
        MarketData market = MarketData.get(server);
        WalletData wallets = WalletData.get(server);

        if (market.isBanned(buyer.getUUID())) {
            return refused(BuyStatus.BANNED, null);
        }
        Listing listing = market.listing(listingId);
        if (listing == null) {
            return refused(BuyStatus.NOT_FOUND, null);
        }
        if (!listing.item().isAvailable()) {
            return refused(BuyStatus.UNAVAILABLE, listing);
        }
        if (Expiry.isExpired(listing.expiresAt(), now)) {
            return refused(BuyStatus.EXPIRED, listing);
        }
        if (listing.price() != expectedPrice) {
            return refused(BuyStatus.PRICE_CHANGED, listing);
        }
        if (listing.seller().equals(buyer.getUUID())) {
            return refused(BuyStatus.OWN_LISTING, listing);
        }

        long price = listing.price();
        UUID buyerId = buyer.getUUID();
        long wallet = wallets.balance(buyerId);
        List<CurrencyPicker.Source> sources = currencySources(buyer);
        if (!CurrencyPicker.canPay(wallet, sources, price)) {
            return refused(BuyStatus.CANNOT_PAY, listing);
        }
        long fromWallet = Math.min(wallet, price);
        long shortfall = price - fromWallet;
        CurrencyPicker.Pick pick = CurrencyPicker.pick(sources, shortfall);
        if (pick == null) {
            return refused(BuyStatus.CANNOT_PAY, listing);
        }

        long cut = Cut.of(price, MarketConfigs.current().cutPercent());
        long sellerShare = price - cut;
        if (!wallets.canAdd(listing.seller(), sellerShare) || !market.canAddToPool(cut)) {
            return refused(BuyStatus.OVERFLOW, listing);
        }

        // Everything that can fail is behind us. From here the steps run in order, on this thread.
        wallets.take(buyerId, fromWallet);
        Inventory inventory = buyer.getInventory();
        for (CurrencyPicker.Take take : pick.takes()) {
            ItemStack slot = inventory.getItem(take.slot());
            slot.shrink(take.count());
            if (slot.isEmpty()) {
                inventory.setItem(take.slot(), ItemStack.EMPTY);
            }
        }
        if (pick.change() > 0) {
            wallets.add(buyerId, pick.change());
        }
        market.removeListing(listingId);
        wallets.add(listing.seller(), sellerShare);
        market.addToPool(cut);
        ItemStack bought = listing.item().stack();
        market.recordSale(new Sale(now, listing.seller(), listing.sellerName(), buyerId, buyer.getGameProfile().getName(),
                listing.item().itemId(), bought.getCount(), price, cut));

        AuditLog.log(server, new AuditEntry(now, "BUY").actor(buyerId, nameOf(buyer))
                .target(listing.seller(), listing.sellerName()).listing(listingId).price(price).cut(cut)
                .item(AuditLog.item(server, listing.item())));

        int given = Giving.give(inventory, bought);
        int left = bought.getCount() - given;
        if (left > 0) {
            ItemStack rest = bought.copy();
            rest.setCount(left);
            market.addReturn(buyerId, StoredStack.of(rest));
        }

        ServerPlayer seller = server.getPlayerList().getPlayer(listing.seller());
        if (seller != null) {
            // The money has moved. A strange item must not stop the rest, so the notice is allowed to fail.
            try {
                seller.sendSystemMessage(Text.translatable("markettrade.sold_notice", buyer.getGameProfile().getName(),
                        bought.getHoverName(), bought.getCount(), show(price), show(sellerShare)));
            } catch (RuntimeException e) {
                LOGGER.error("Could not tell the seller about listing {}", listingId, e);
            }
        }
        return new BuyResult(BuyStatus.OK, price, cut, bought, left);
    }

    private static BuyResult refused(BuyStatus status, Listing listing) {
        ItemStack item = listing != null && listing.item().isAvailable() ? listing.item().stack() : ItemStack.EMPTY;
        return new BuyResult(status, listing == null ? 0 : listing.price(), 0, item, 0);
    }

    private static List<CurrencyPicker.Source> currencySources(ServerPlayer player) {
        CurrencyConfig currency = Currencies.current();
        List<CurrencyPicker.Source> sources = new ArrayList<>();
        if (!currency.hasItems()) {
            return sources;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot <= OFFHAND_SLOT; slot++) {
            if (slot >= MAIN_SLOTS && slot != OFFHAND_SLOT) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isComponentsPatchEmpty()) {
                continue;
            }
            CurrencyItem entry = currency.find(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (entry != null) {
                sources.add(new CurrencyPicker.Source(slot, entry.value(), stack.getCount()));
            }
        }
        return sources;
    }

    /** The wallet plus the value of plain currency items the player carries. Matches what {@link #buy} can pay with. */
    public static long spendable(ServerPlayer player) {
        long total = WalletData.get(player.server).balance(player.getUUID());
        for (CurrencyPicker.Source source : currencySources(player)) {
            long value;
            try {
                value = Math.multiplyExact(source.value(), (long) source.count());
                total = Math.addExact(total, value);
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    // Returns

    public static int returnsCount(MinecraftServer server, UUID player) {
        return MarketData.get(server).returnsOf(player).size();
    }

    /** Gives what fits, oldest first. What doesn't fit, and anything unavailable, stays in the list. */
    public static ClaimResult claimReturns(ServerPlayer player) {
        MarketData market = MarketData.get(player.server);
        UUID id = player.getUUID();
        List<StoredStack> before = market.returnsOf(id);
        if (before.isEmpty()) {
            return new ClaimResult(0, 0, 0);
        }
        Inventory inventory = player.getInventory();
        List<StoredStack> after = new ArrayList<>();
        int claimed = 0;
        int unavailable = 0;
        for (StoredStack stored : before) {
            if (!stored.isAvailable()) {
                after.add(stored);
                unavailable++;
                continue;
            }
            ItemStack stack = stored.stack();
            int given = Giving.give(inventory, stack);
            claimed += given;
            if (given < stack.getCount()) {
                after.add(stored.withCount(stack.getCount() - given));
            }
        }
        market.replaceReturns(id, after);
        return new ClaimResult(claimed, after.size(), unavailable);
    }

    /**
     * Claims one stack from Returns. {@code index} and {@code expected} are what the player was shown, and the claim only
     * goes ahead if that entry is still there. What doesn't fit stays in the list.
     */
    public static ClaimOneResult claimReturn(ServerPlayer player, int index, StoredStack expected) {
        MarketData market = MarketData.get(player.server);
        UUID id = player.getUUID();
        List<StoredStack> entries = new ArrayList<>(market.returnsOf(id));
        if (index < 0 || index >= entries.size() || !entries.get(index).sameAs(expected)) {
            return new ClaimOneResult(ClaimOneStatus.GONE, ItemStack.EMPTY, 0, 0);
        }
        StoredStack entry = entries.get(index);
        if (!entry.isAvailable()) {
            return new ClaimOneResult(ClaimOneStatus.UNAVAILABLE, ItemStack.EMPTY, 0, entry.count());
        }
        ItemStack stack = entry.stack();
        int given = Giving.give(player.getInventory(), stack);
        if (given <= 0) {
            return new ClaimOneResult(ClaimOneStatus.NO_ROOM, stack, 0, stack.getCount());
        }
        int left = stack.getCount() - given;
        if (left > 0) {
            entries.set(index, entry.withCount(left));
        } else {
            entries.remove(index);
        }
        market.replaceReturns(id, entries);
        return new ClaimOneResult(left > 0 ? ClaimOneStatus.PARTIAL : ClaimOneStatus.OK, stack, given, left);
    }

    // Renames

    /** Called when a player takes an anvil result. Logs it if the custom name is new. */
    public static void logRename(ServerPlayer player, ItemStack left, ItemStack output) {
        if (output.isEmpty()) {
            return;
        }
        Component after = output.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        Component before = left.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (after == null || after.equals(before)) {
            return;
        }
        AuditLog.log(player.server, new AuditEntry(System.currentTimeMillis(), "RENAME")
                .actor(player.getUUID(), nameOf(player)).item(AuditLog.item(player.server, StoredStack.of(output))));
    }

    // Reports

    /** Files a report. The reason is optional and cut to {@link #MAX_REASON_LENGTH} characters. */
    public static ReportResult report(ServerPlayer player, long listingId, String reason, long now) {
        MinecraftServer server = player.server;
        MarketData market = MarketData.get(server);
        UUID id = player.getUUID();
        if (market.isBanned(id)) {
            return new ReportResult(ReportStatus.BANNED, 0, 0);
        }
        Listing listing = market.listing(listingId);
        if (listing == null) {
            return new ReportResult(ReportStatus.NOT_FOUND, 0, 0);
        }
        if (listing.seller().equals(id)) {
            return new ReportResult(ReportStatus.OWN_LISTING, 0, 0);
        }
        if (market.hasReported(id, listingId)) {
            return new ReportResult(ReportStatus.ALREADY_REPORTED, 0, 0);
        }
        long cooldownMillis = MarketConfigs.current().reportCooldownSeconds() * 1000L;
        long last = market.lastReportTime(id);
        if (last != Long.MIN_VALUE && now - last < cooldownMillis) {
            long wait = (cooldownMillis - (now - last) + 999) / 1000;
            return new ReportResult(ReportStatus.COOLDOWN, 0, wait);
        }

        String cleaned = reason == null ? "" : cutReason(reason.strip());
        long reportId = market.nextReportId();
        market.addReport(new Report(reportId, now, id, nameOf(player), listing.seller(), listing.sellerName(), listingId,
                listing.item(), cleaned, true));
        market.setLastReportTime(id, now);
        AuditLog.log(server, new AuditEntry(now, "REPORT").actor(id, nameOf(player))
                .target(listing.seller(), listing.sellerName()).listing(listingId).price(listing.price())
                .reason("report " + reportId + (cleaned.isEmpty() ? "" : ": " + cleaned))
                .item(AuditLog.item(server, listing.item())));
        Component itemName = listing.item().isAvailable() ? display(listing.item()).getHoverName()
                : Component.literal(listing.item().itemId());
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (admin.hasPermissions(2)) {
                admin.sendSystemMessage(Text.translatable("markettrade.report_notice", reportId, listing.sellerName(),
                        itemName));
            }
        }
        return new ReportResult(ReportStatus.OK, reportId, 0);
    }

    private static String cutReason(String reason) {
        int[] points = reason.codePoints().toArray();
        if (points.length <= MAX_REASON_LENGTH) {
            return reason;
        }
        return new String(points, 0, MAX_REASON_LENGTH);
    }

    public static CloseStatus closeReport(MinecraftServer server, Actor actor, long reportId) {
        MarketData market = MarketData.get(server);
        Report report = market.report(reportId);
        if (report == null) {
            return CloseStatus.NOT_FOUND;
        }
        if (market.closeReport(reportId) == null) {
            return CloseStatus.ALREADY_CLOSED;
        }
        AuditLog.log(server, new AuditEntry(System.currentTimeMillis(), "REPORT_CLOSE").actor(actor)
                .target(report.seller(), report.sellerName()).listing(report.listingId())
                .reason("report " + reportId).item(AuditLog.item(server, report.item())));
        return CloseStatus.OK;
    }

    // Shop bans

    /** Bans the player from selling, buying and reporting, and moves every listing of theirs to their Returns. */
    public static BanResult shopBan(MinecraftServer server, Actor actor, UUID player, String name, String reason) {
        MarketData market = MarketData.get(server);
        if (market.isBanned(player)) {
            return new BanResult(BanStatus.ALREADY_BANNED, 0);
        }
        long now = System.currentTimeMillis();
        String cleaned = reason == null ? "" : cutReason(reason.strip());
        market.addBan(new ShopBan(player, name, actor.id(), actor.name(), cleaned, now));
        AuditLog.log(server, new AuditEntry(now, "SHOP_BAN").actor(actor).target(player, name).reason(cleaned));
        int moved = 0;
        for (Listing listing : market.listingsOf(player)) {
            market.removeListing(listing.id());
            market.addReturn(listing.seller(), listing.item());
            moved++;
            AuditLog.log(server, new AuditEntry(now, "ADMIN_REMOVE").actor(actor)
                    .target(listing.seller(), listing.sellerName()).listing(listing.id()).price(listing.price())
                    .reason("shop ban").item(AuditLog.item(server, listing.item())));
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(Text.translatable("markettrade.banned_notice"));
        }
        return new BanResult(BanStatus.OK, moved);
    }

    /** Lifts the ban. Listings that were moved to Returns are not put back. */
    public static BanResult shopUnban(MinecraftServer server, Actor actor, UUID player, String name) {
        MarketData market = MarketData.get(server);
        if (market.removeBan(player) == null) {
            return new BanResult(BanStatus.NOT_BANNED, 0);
        }
        AuditLog.log(server, new AuditEntry(System.currentTimeMillis(), "SHOP_UNBAN").actor(actor).target(player, name));
        return new BanResult(BanStatus.OK, 0);
    }

    // Pool

    /** Pays from the pool into a wallet, online or not. Every check comes first, then the pool is taken from, then the wallet. */
    public static PoolResult payFromPool(MinecraftServer server, Actor actor, UUID target, String targetName, long amount) {
        MarketData market = MarketData.get(server);
        WalletData wallets = WalletData.get(server);
        long pool = market.pool();
        long balance = wallets.balance(target);
        if (amount < 1) {
            return new PoolResult(PoolStatus.BAD_AMOUNT, pool, balance);
        }
        if (amount > pool) {
            return new PoolResult(PoolStatus.NOT_ENOUGH, pool, balance);
        }
        if (!wallets.canAdd(target, amount)) {
            return new PoolResult(PoolStatus.WALLET_FULL, pool, balance);
        }
        if (!market.takeFromPool(amount)) {
            return new PoolResult(PoolStatus.NOT_ENOUGH, pool, balance);
        }
        if (!wallets.add(target, amount)) {
            market.setPool(pool);
            return new PoolResult(PoolStatus.WALLET_FULL, pool, balance);
        }

        long now = System.currentTimeMillis();
        long poolAfter = market.pool();
        long balanceAfter = wallets.balance(target);
        market.addLedger(new PoolEntry(now, PoolEntry.Kind.PAYOUT, amount, poolAfter, actor.id(), actor.name(), target,
                targetName, ""));
        AuditLog.log(server, new AuditEntry(now, "POOL_PAYOUT").actor(actor).target(target, targetName).price(amount)
                .reason("pool now " + poolAfter));
        try {
            ServerPlayer online = server.getPlayerList().getPlayer(target);
            if (online != null) {
                online.sendSystemMessage(Text.translatable("markettrade.pool_paid_notice",
                        Currencies.current().formatWithTotal(amount)));
            }
        } catch (RuntimeException e) {
            LOGGER.error("Could not tell {} about their pool payout. The money was paid.", targetName, e);
        }
        return new PoolResult(PoolStatus.OK, poolAfter, balanceAfter);
    }

    /** Sets the pool to a new amount by hand. */
    public static PoolResult adjustPool(MinecraftServer server, Actor actor, long newAmount, String note) {
        MarketData market = MarketData.get(server);
        if (newAmount < 0) {
            return new PoolResult(PoolStatus.BAD_AMOUNT, market.pool(), 0);
        }
        long now = System.currentTimeMillis();
        String cleaned = note == null ? "" : cutReason(note.strip());
        long before = market.pool();
        market.setPool(newAmount);
        market.addLedger(new PoolEntry(now, PoolEntry.Kind.ADJUST, newAmount, newAmount, actor.id(), actor.name(), null, "",
                cleaned));
        AuditLog.log(server, new AuditEntry(now, "POOL_ADJUST").actor(actor).price(newAmount)
                .reason("was " + before + (cleaned.isEmpty() ? "" : ", " + cleaned)));
        return new PoolResult(PoolStatus.OK, newAmount, 0);
    }

    /** Top sellers by lifetime earnings. Names come from the newest sale, then the profile cache, then the id. */
    public static List<TopSellers.Row> topSellers(MinecraftServer server) {
        SalesLog sales = MarketData.get(server).sales();
        return TopSellers.rank(sales.lifetimeTotals(), sales.recent(), id -> {
            try {
                return server.getProfileCache() == null ? null
                        : server.getProfileCache().get(id).map(profile -> profile.getName()).orElse(null);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    // Helpers

    private static String nameOf(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** A copy to show a name from. Empty for an unavailable stack. */
    public static ItemStack display(StoredStack stored) {
        return stored.isAvailable() ? stored.stack() : ItemStack.EMPTY;
    }

    private static String show(long amount) {
        return Currencies.current().format(amount);
    }
}
