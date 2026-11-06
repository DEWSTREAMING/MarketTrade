package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;

import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.mojang.logging.LogUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Plain records for the client screen, built from the market on the server thread. Nothing here changes anything. Item data
 * that can't be shown safely (too big for a packet, or not readable) is left out and the row says so, because the server must
 * never fail after money has moved and the client must never be disconnected by size.
 */
public final class MarketViews {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Minecraft frames cap near 2 MB and NeoForge only splits above 8 MB, so one payload must stay well under 2 MB even if it
     * doesn't compress. One stack above {@link #STACK_BUDGET}, or a page above {@link #PAGE_BUDGET}, is sent without its data.
     */
    public static final int STACK_BUDGET = 120_000;
    public static final int PAGE_BUDGET = 600_000;
    public static final int MAX_PAGE_SIZE = 12;

    /** The wallet line and counts the header needs. {@code spendable} is the wallet plus currency items in the inventory. */
    public record Status(long balance, String balanceText, long spendable, int myListings, int returns, boolean banned) {
    }

    /**
     * One listing. {@code stack} is empty when the item is unavailable or its data is hidden; {@code itemId} is always set.
     * {@code msLeft} is the time until it expires.
     */
    public record ListingRow(long id, ItemStack stack, String itemId, boolean available, boolean detailsHidden, int count,
            String seller, boolean yours, long price, String priceText, String totalText, long msLeft) {
    }

    public record ItemPage(String itemId, boolean available, int listed, long lowest, String lowestText, long[] chart,
            String averageText, int page, int pages, List<ListingRow> rows) {
    }

    public record MyListings(int page, int pages, int total, List<ListingRow> rows) {
    }

    /** {@code token} is the hash of the item and its data, so a claim can name the stack it saw without sending it. */
    public record ReturnRow(int index, ItemStack stack, String itemId, boolean available, boolean detailsHidden, int count,
            int token) {
    }

    public record Returns(int page, int pages, int total, List<ReturnRow> rows) {
    }

    public record CurrencyButton(String itemId, String name, long value, int maxStack) {
    }

    public record WalletView(String balanceText, boolean hasItems, String currencyName, List<CurrencyButton> buttons) {
    }

    public enum SellStatus {
        OK, EMPTY_SLOT, CHANGED, EMPTY_TEXT, BAD_AMOUNT, TOO_BIG, LIMIT, BANNED
    }

    /** {@code asked} is the price text this answers. {@code token} is what a confirm sends back to name the stack in the slot. */
    public record SellPreview(SellStatus status, String priceText, String cutText, String receiveText, int cutPercent,
            int limit, String asked, int token) {
    }

    private MarketViews() {
    }

    // Status

    public static Status status(ServerPlayer player) {
        MinecraftServer server = player.server;
        MarketData market = MarketData.get(server);
        long balance = WalletData.get(server).balance(player.getUUID());
        return new Status(balance, show(balance), MarketActions.spendable(player), market.countListingsOf(player.getUUID()),
                market.returnsOf(player.getUUID()).size(), market.isBanned(player.getUUID()));
    }

    // Browse

    /** One row per item, for every listing that could still be bought. Only ids and numbers. */
    public static List<SummaryRow> summary(MinecraftServer server, long now) {
        List<SummaryRows.Entry> entries = new ArrayList<>();
        for (Listing listing : MarketData.get(server).listingsNewestFirst()) {
            if (listing.item().isAvailable() && Expiry.isExpired(listing.expiresAt(), now)) {
                continue;
            }
            entries.add(new SummaryRows.Entry(listing.item().itemId(), listing.price(), listing.item().isAvailable()));
        }
        return SummaryRows.group(entries, MarketViews::show);
    }

    // Item page

    public static ItemPage itemPage(ServerPlayer viewer, RegistryAccess registries, String itemId, int page, int pageSize,
            long now) {
        MarketData market = MarketData.get(viewer.server);
        List<Listing> matching = new ArrayList<>();
        for (Listing listing : market.listingsNewestFirst()) {
            if (!listing.item().itemId().equals(itemId)) {
                continue;
            }
            if (listing.item().isAvailable() && Expiry.isExpired(listing.expiresAt(), now)) {
                continue;
            }
            matching.add(listing);
        }
        matching.sort(java.util.Comparator.comparingLong(Listing::price).thenComparingLong(Listing::id));

        int size = clampSize(pageSize);
        int pages = Pagination.totalPages(matching.size(), size);
        int clamped = Pagination.clampPage(page, pages);
        int from = Pagination.startIndex(clamped, size);
        int to = Pagination.endIndex(clamped, size, matching.size());
        List<ListingRow> rows = new ArrayList<>();
        int[] budget = { PAGE_BUDGET };
        for (int i = from; i < to; i++) {
            rows.add(row(matching.get(i), viewer.getUUID(), registries, now, budget));
        }
        long lowest = matching.isEmpty() ? 0 : matching.get(0).price();
        SalesChart.Chart chart = SalesChart.build(market.sales().recent(), itemId, now);
        boolean available = matching.isEmpty() || matching.stream().allMatch(l -> l.item().isAvailable());
        return new ItemPage(itemId, available, matching.size(), lowest, matching.isEmpty() ? "" : show(lowest), chart.days(),
                chart.average() < 0 ? "" : show(chart.average()), clamped, pages, rows);
    }

    // My listings and Returns

    public static MyListings myListings(ServerPlayer player, RegistryAccess registries, int page, int pageSize, long now) {
        List<Listing> mine = MarketData.get(player.server).listingsOf(player.getUUID());
        int size = clampSize(pageSize);
        int pages = Pagination.totalPages(mine.size(), size);
        int clamped = Pagination.clampPage(page, pages);
        List<ListingRow> rows = new ArrayList<>();
        int[] budget = { PAGE_BUDGET };
        for (int i = Pagination.startIndex(clamped, size); i < Pagination.endIndex(clamped, size, mine.size()); i++) {
            rows.add(row(mine.get(i), player.getUUID(), registries, now, budget));
        }
        return new MyListings(clamped, pages, mine.size(), rows);
    }

    public static Returns returns(ServerPlayer player, RegistryAccess registries, int page, int pageSize) {
        List<StoredStack> all = MarketData.get(player.server).returnsOf(player.getUUID());
        int size = clampSize(pageSize);
        int pages = Pagination.totalPages(all.size(), size);
        int clamped = Pagination.clampPage(page, pages);
        List<ReturnRow> rows = new ArrayList<>();
        int[] budget = { PAGE_BUDGET };
        for (int i = Pagination.startIndex(clamped, size); i < Pagination.endIndex(clamped, size, all.size()); i++) {
            StoredStack stored = all.get(i);
            ItemStack shown = shownStack(stored, registries, budget);
            int token = stored.isAvailable() ? token(stored.stack()) : 0;
            rows.add(new ReturnRow(i, shown, stored.itemId(), stored.isAvailable(), stored.isAvailable() && shown.isEmpty(),
                    stored.count(), token));
        }
        return new Returns(clamped, pages, all.size(), rows);
    }

    // Wallet

    public static WalletView wallet(ServerPlayer player) {
        CurrencyConfig currency = Currencies.current();
        List<CurrencyButton> buttons = new ArrayList<>();
        for (CurrencyItem entry : currency.items()) {
            Item item = itemOf(entry.item());
            if (item != null) {
                buttons.add(new CurrencyButton(entry.item(), entry.name(), entry.value(),
                        new ItemStack(item).getMaxStackSize()));
            }
        }
        long balance = WalletData.get(player.server).balance(player.getUUID());
        return new WalletView(show(balance), currency.hasItems(), currency.name(), buttons);
    }

    // Selling

    /**
     * What listing the stack in {@code slot} at {@code priceText} would do. Nothing is changed. {@code itemId} is the item's
     * network id, which both sides share, and the answer carries the server's token for the confirm.
     */
    public static SellPreview sellPreview(ServerPlayer player, int slot, int count, int itemId, String priceText) {
        MarketConfig config = MarketConfigs.current();
        int cutPercent = config.cutPercent();
        if (MarketData.get(player.server).isBanned(player.getUUID())) {
            return preview(SellStatus.BANNED, cutPercent, config, priceText, 0);
        }
        ItemStack stack = slot >= 0 && slot < 36 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        if (stack.isEmpty()) {
            return preview(SellStatus.EMPTY_SLOT, cutPercent, config, priceText, 0);
        }
        if (stack.getCount() != count || BuiltInRegistries.ITEM.getId(stack.getItem()) != itemId) {
            return preview(SellStatus.CHANGED, cutPercent, config, priceText, 0);
        }
        AmountParser.Result parsed = AmountParser.parse(priceText, Currencies.current());
        if (!parsed.ok()) {
            SellStatus status = switch (parsed.status()) {
                case EMPTY -> SellStatus.EMPTY_TEXT;
                case TOO_BIG -> SellStatus.TOO_BIG;
                default -> SellStatus.BAD_AMOUNT;
            };
            return preview(status, cutPercent, config, priceText, 0);
        }
        long price = parsed.amount();
        if (price < 1) {
            return preview(SellStatus.BAD_AMOUNT, cutPercent, config, priceText, 0);
        }
        if (MarketData.get(player.server).countListingsOf(player.getUUID()) >= config.maxListingsPerPlayer()) {
            return preview(SellStatus.LIMIT, cutPercent, config, priceText, 0);
        }
        long cut = Cut.of(price, cutPercent);
        return new SellPreview(SellStatus.OK, show(price), show(cut), show(price - cut), cutPercent,
                config.maxListingsPerPlayer(), priceText, token(stack));
    }

    private static SellPreview preview(SellStatus status, int cutPercent, MarketConfig config, String asked, int token) {
        return new SellPreview(status, "", "", "", cutPercent, config.maxListingsPerPlayer(), asked, token);
    }

    // Tokens

    /**
     * A short stand-in for a stack: the item and its data hashed together. The count goes next to it. The hash uses object
     * identity, so it only means anything on the server: a client must echo a token it was sent and can never make one.
     */
    public static int token(ItemStack stack) {
        return ItemStack.hashItemAndComponents(stack);
    }

    public static boolean matchesToken(ItemStack stack, int count, int token) {
        return !stack.isEmpty() && stack.getCount() == count && token(stack) == token;
    }

    // Helpers

    public static int clampSize(int pageSize) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    public static Item itemOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    private static ListingRow row(Listing listing, UUID viewer, RegistryAccess registries, long now, int[] budget) {
        StoredStack stored = listing.item();
        ItemStack shown = shownStack(stored, registries, budget);
        long price = listing.price();
        String priceText = show(price);
        String total = Currencies.current().plainTotal(price);
        return new ListingRow(listing.id(), shown, stored.itemId(), stored.isAvailable(),
                stored.isAvailable() && shown.isEmpty(), stored.count(), listing.sellerName(),
                listing.seller().equals(viewer), price, priceText, total.equals(priceText) ? "" : total,
                Math.max(0, listing.expiresAt() - now));
    }

    /**
     * The stack to put in a packet, or empty when it is unavailable, unreadable or too big for what is left of the budget.
     * Never throws.
     */
    private static ItemStack shownStack(StoredStack stored, RegistryAccess registries, int[] budget) {
        if (!stored.isAvailable()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = stored.stack();
            int size = encodedSize(stack, registries);
            if (size > STACK_BUDGET || size > budget[0]) {
                return ItemStack.EMPTY;
            }
            budget[0] -= size;
            return stack;
        } catch (RuntimeException e) {
            LOGGER.error("Could not measure the item {} for the screen. Sending its id only.", stored.itemId(), e);
            return ItemStack.EMPTY;
        }
    }

    public static int encodedSize(ItemStack stack, RegistryAccess registries) {
        ByteBuf raw = Unpooled.buffer();
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(new RegistryFriendlyByteBuf(raw, registries), stack);
            return raw.readableBytes();
        } finally {
            raw.release();
        }
    }

    private static String show(long amount) {
        return Currencies.current().format(amount);
    }
}
