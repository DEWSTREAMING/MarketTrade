package com.markettrade.net;

import java.util.ArrayList;
import java.util.List;

import com.markettrade.MarketTrade;
import com.markettrade.market.MarketViews;
import com.markettrade.market.SummaryRow;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Every payload of the client screen. Requests go to the server and are only requests. Data comes back as plain records. Every
 * string and list has a limit, so a bad client can't make the server read or build something huge.
 */
public final class MarketPackets {
    public static final int MAX_ID = 256;
    public static final int MAX_PRICE_TEXT = 64;
    public static final int MAX_REASON = 200;
    // Going over one of these on the way out would disconnect the player, so they are far above any real id or amount.
    public static final int MAX_TEXT = 256;
    public static final int MAX_ROWS = 64;
    public static final int MAX_CHUNK_ROWS = 400;
    public static final int MAX_LINES = 6;

    private MarketPackets() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> channelType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MarketTrade.MODID, path));
    }

    private static StreamCodec<ByteBuf, String> str(int max) {
        return ByteBufCodecs.stringUtf8(max);
    }

    // Codec helpers: write fields one after another, read them back in the same order.

    private static void writeStack(RegistryFriendlyByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
    }

    private static ItemStack readStack(RegistryFriendlyByteBuf buf) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
    }

    // Requests (client to server)

    public enum ViewKind {
        SUMMARY, ITEM, MINE, RETURNS, WALLET, STATUS
    }

    /** Asks for the data of one screen. {@code text} is an item id for ITEM. {@code generation} comes back with the answer. */
    public record ViewRequest(int kind, int page, int pageSize, String text, int generation) implements CustomPacketPayload {
        public static final Type<ViewRequest> TYPE = channelType("view_request");
        public static final StreamCodec<ByteBuf, ViewRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ViewRequest::kind, ByteBufCodecs.VAR_INT, ViewRequest::page,
                ByteBufCodecs.VAR_INT, ViewRequest::pageSize, str(MAX_ID), ViewRequest::text,
                ByteBufCodecs.VAR_INT, ViewRequest::generation, ViewRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BuyRequest(long id, long price) implements CustomPacketPayload {
        public static final Type<BuyRequest> TYPE = channelType("buy_request");
        public static final StreamCodec<ByteBuf, BuyRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, BuyRequest::id, ByteBufCodecs.VAR_LONG, BuyRequest::price, BuyRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * {@code count} and {@code token} name the stack the player saw in the slot, without sending it. With {@code confirm}
     * false this only asks for a preview, and {@code token} is the item's network id. With it true, {@code token} is the one
     * the preview returned.
     */
    public record SellRequest(int slot, int count, int token, String priceText, boolean confirm) implements CustomPacketPayload {
        public static final Type<SellRequest> TYPE = channelType("sell_request");
        public static final StreamCodec<ByteBuf, SellRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SellRequest::slot, ByteBufCodecs.VAR_INT, SellRequest::count,
                ByteBufCodecs.INT, SellRequest::token, str(MAX_PRICE_TEXT), SellRequest::priceText,
                ByteBufCodecs.BOOL, SellRequest::confirm, SellRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CancelRequest(long id) implements CustomPacketPayload {
        public static final Type<CancelRequest> TYPE = channelType("cancel_request");
        public static final StreamCodec<ByteBuf, CancelRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, CancelRequest::id, CancelRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code index} -1 claims everything. Otherwise {@code count} and {@code token} name the stack the player saw. */
    public record ClaimRequest(int index, int count, int token) implements CustomPacketPayload {
        public static final Type<ClaimRequest> TYPE = channelType("claim_request");
        public static final StreamCodec<ByteBuf, ClaimRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ClaimRequest::index, ByteBufCodecs.VAR_INT, ClaimRequest::count,
                ByteBufCodecs.INT, ClaimRequest::token, ClaimRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum WalletKind {
        DEPOSIT_ALL, WITHDRAW_ITEM, WITHDRAW_STACK, WITHDRAW_AMOUNT
    }

    /** {@code itemId} and {@code count} for the item kinds, {@code text} (an amount) for WITHDRAW_AMOUNT. */
    public record WalletRequest(int kind, String itemId, int count, String text) implements CustomPacketPayload {
        public static final Type<WalletRequest> TYPE = channelType("wallet_request");
        public static final StreamCodec<ByteBuf, WalletRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WalletRequest::kind, str(MAX_ID), WalletRequest::itemId,
                ByteBufCodecs.VAR_INT, WalletRequest::count, str(MAX_PRICE_TEXT), WalletRequest::text,
                WalletRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The open key. The server picks the screen, as it does for {@code /market}. */
    public record OpenRequest() implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = channelType("open_request");
        public static final StreamCodec<ByteBuf, OpenRequest> CODEC = StreamCodec.unit(new OpenRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ReportRequest(long id, String reason) implements CustomPacketPayload {
        public static final Type<ReportRequest> TYPE = channelType("report_request");
        public static final StreamCodec<ByteBuf, ReportRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, ReportRequest::id, str(MAX_REASON), ReportRequest::reason, ReportRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // Data (server to client)

    public record StatusPayload(MarketViews.Status status) implements CustomPacketPayload {
        public static final Type<StatusPayload> TYPE = channelType("status");
        public static final StreamCodec<ByteBuf, StatusPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    MarketViews.Status s = p.status();
                    ByteBufCodecs.VAR_LONG.encode(buf, s.balance());
                    str(MAX_TEXT).encode(buf, s.balanceText());
                    ByteBufCodecs.VAR_LONG.encode(buf, s.spendable());
                    ByteBufCodecs.VAR_INT.encode(buf, s.myListings());
                    ByteBufCodecs.VAR_INT.encode(buf, s.returns());
                    ByteBufCodecs.BOOL.encode(buf, s.banned());
                },
                buf -> new StatusPayload(new MarketViews.Status(ByteBufCodecs.VAR_LONG.decode(buf),
                        str(MAX_TEXT).decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Tells the client to open the screen. */
    public record OpenScreen(int tab) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = channelType("open_market");
        public static final StreamCodec<ByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenScreen::tab, OpenScreen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A few hundred summary rows. {@code first} clears what the client had. {@code total} is the number of rows overall. */
    public record SummaryChunk(int generation, int total, boolean first, List<SummaryRow> rows) implements CustomPacketPayload {
        public static final Type<SummaryChunk> TYPE = channelType("summary_chunk");
        private static final StreamCodec<ByteBuf, SummaryRow> ROW = StreamCodec.of(
                (buf, r) -> {
                    str(MAX_ID).encode(buf, r.itemId());
                    ByteBufCodecs.VAR_INT.encode(buf, r.listed());
                    ByteBufCodecs.VAR_LONG.encode(buf, r.lowest());
                    str(MAX_TEXT).encode(buf, r.lowestText());
                    ByteBufCodecs.BOOL.encode(buf, r.available());
                },
                buf -> new SummaryRow(str(MAX_ID).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf), str(MAX_TEXT).decode(buf), ByteBufCodecs.BOOL.decode(buf)));
        public static final StreamCodec<ByteBuf, SummaryChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SummaryChunk::generation, ByteBufCodecs.VAR_INT, SummaryChunk::total,
                ByteBufCodecs.BOOL, SummaryChunk::first, ROW.apply(ByteBufCodecs.list(MAX_CHUNK_ROWS)), SummaryChunk::rows,
                SummaryChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeListing(RegistryFriendlyByteBuf buf, MarketViews.ListingRow r) {
        ByteBufCodecs.VAR_LONG.encode(buf, r.id());
        writeStack(buf, r.stack());
        str(MAX_ID).encode(buf, r.itemId());
        ByteBufCodecs.BOOL.encode(buf, r.available());
        ByteBufCodecs.BOOL.encode(buf, r.detailsHidden());
        ByteBufCodecs.VAR_INT.encode(buf, r.count());
        str(MAX_TEXT).encode(buf, r.seller());
        ByteBufCodecs.BOOL.encode(buf, r.yours());
        ByteBufCodecs.VAR_LONG.encode(buf, r.price());
        str(MAX_TEXT).encode(buf, r.priceText());
        str(MAX_TEXT).encode(buf, r.totalText());
        ByteBufCodecs.VAR_LONG.encode(buf, r.msLeft());
    }

    private static MarketViews.ListingRow readListing(RegistryFriendlyByteBuf buf) {
        long id = ByteBufCodecs.VAR_LONG.decode(buf);
        ItemStack stack = readStack(buf);
        String itemId = str(MAX_ID).decode(buf);
        boolean available = ByteBufCodecs.BOOL.decode(buf);
        boolean hidden = ByteBufCodecs.BOOL.decode(buf);
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        String seller = str(MAX_TEXT).decode(buf);
        boolean yours = ByteBufCodecs.BOOL.decode(buf);
        long price = ByteBufCodecs.VAR_LONG.decode(buf);
        String priceText = str(MAX_TEXT).decode(buf);
        String totalText = str(MAX_TEXT).decode(buf);
        long msLeft = ByteBufCodecs.VAR_LONG.decode(buf);
        return new MarketViews.ListingRow(id, stack, itemId, available, hidden, count, seller, yours, price, priceText,
                totalText, msLeft);
    }

    private static List<MarketViews.ListingRow> readListings(RegistryFriendlyByteBuf buf) {
        int n = ByteBufCodecs.VAR_INT.decode(buf);
        if (n < 0 || n > MAX_ROWS) {
            throw new io.netty.handler.codec.DecoderException("Too many rows: " + n);
        }
        List<MarketViews.ListingRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(readListing(buf));
        }
        return rows;
    }

    private static void writeListings(RegistryFriendlyByteBuf buf, List<MarketViews.ListingRow> rows) {
        ByteBufCodecs.VAR_INT.encode(buf, rows.size());
        for (MarketViews.ListingRow row : rows) {
            writeListing(buf, row);
        }
    }

    public record ItemPagePayload(int generation, MarketViews.ItemPage page) implements CustomPacketPayload {
        public static final Type<ItemPagePayload> TYPE = channelType("item_page");
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemPagePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    MarketViews.ItemPage v = p.page();
                    ByteBufCodecs.VAR_INT.encode(buf, p.generation());
                    str(MAX_ID).encode(buf, v.itemId());
                    ByteBufCodecs.BOOL.encode(buf, v.available());
                    ByteBufCodecs.VAR_INT.encode(buf, v.listed());
                    ByteBufCodecs.VAR_LONG.encode(buf, v.lowest());
                    str(MAX_TEXT).encode(buf, v.lowestText());
                    for (long day : v.chart()) {
                        ByteBufCodecs.VAR_LONG.encode(buf, day + 1);
                    }
                    str(MAX_TEXT).encode(buf, v.averageText());
                    ByteBufCodecs.VAR_INT.encode(buf, v.page());
                    ByteBufCodecs.VAR_INT.encode(buf, v.pages());
                    writeListings(buf, v.rows());
                },
                buf -> {
                    int generation = ByteBufCodecs.VAR_INT.decode(buf);
                    String itemId = str(MAX_ID).decode(buf);
                    boolean available = ByteBufCodecs.BOOL.decode(buf);
                    int listed = ByteBufCodecs.VAR_INT.decode(buf);
                    long lowest = ByteBufCodecs.VAR_LONG.decode(buf);
                    String lowestText = str(MAX_TEXT).decode(buf);
                    long[] chart = new long[com.markettrade.market.SalesChart.DAYS];
                    for (int i = 0; i < chart.length; i++) {
                        chart[i] = ByteBufCodecs.VAR_LONG.decode(buf) - 1;
                    }
                    String average = str(MAX_TEXT).decode(buf);
                    int page = ByteBufCodecs.VAR_INT.decode(buf);
                    int pages = ByteBufCodecs.VAR_INT.decode(buf);
                    return new ItemPagePayload(generation, new MarketViews.ItemPage(itemId, available, listed, lowest,
                            lowestText, chart, average, page, pages, readListings(buf)));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MyListingsPayload(int generation, MarketViews.MyListings data) implements CustomPacketPayload {
        public static final Type<MyListingsPayload> TYPE = channelType("my_listings");
        public static final StreamCodec<RegistryFriendlyByteBuf, MyListingsPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.generation());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().page());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().pages());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().total());
                    writeListings(buf, p.data().rows());
                },
                buf -> {
                    int generation = ByteBufCodecs.VAR_INT.decode(buf);
                    int page = ByteBufCodecs.VAR_INT.decode(buf);
                    int pages = ByteBufCodecs.VAR_INT.decode(buf);
                    int total = ByteBufCodecs.VAR_INT.decode(buf);
                    return new MyListingsPayload(generation, new MarketViews.MyListings(page, pages, total, readListings(buf)));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ReturnsPayload(int generation, MarketViews.Returns data) implements CustomPacketPayload {
        public static final Type<ReturnsPayload> TYPE = channelType("returns");
        public static final StreamCodec<RegistryFriendlyByteBuf, ReturnsPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.generation());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().page());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().pages());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().total());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().rows().size());
                    for (MarketViews.ReturnRow r : p.data().rows()) {
                        ByteBufCodecs.VAR_INT.encode(buf, r.index());
                        writeStack(buf, r.stack());
                        str(MAX_ID).encode(buf, r.itemId());
                        ByteBufCodecs.BOOL.encode(buf, r.available());
                        ByteBufCodecs.BOOL.encode(buf, r.detailsHidden());
                        ByteBufCodecs.VAR_INT.encode(buf, r.count());
                        ByteBufCodecs.INT.encode(buf, r.token());
                    }
                },
                buf -> {
                    int generation = ByteBufCodecs.VAR_INT.decode(buf);
                    int page = ByteBufCodecs.VAR_INT.decode(buf);
                    int pages = ByteBufCodecs.VAR_INT.decode(buf);
                    int total = ByteBufCodecs.VAR_INT.decode(buf);
                    int n = ByteBufCodecs.VAR_INT.decode(buf);
                    if (n < 0 || n > MAX_ROWS) {
                        throw new io.netty.handler.codec.DecoderException("Too many rows: " + n);
                    }
                    List<MarketViews.ReturnRow> rows = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        rows.add(new MarketViews.ReturnRow(ByteBufCodecs.VAR_INT.decode(buf), readStack(buf),
                                str(MAX_ID).decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf)));
                    }
                    return new ReturnsPayload(generation, new MarketViews.Returns(page, pages, total, rows));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WalletPayload(MarketViews.WalletView data) implements CustomPacketPayload {
        public static final Type<WalletPayload> TYPE = channelType("wallet");
        public static final StreamCodec<ByteBuf, WalletPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    str(MAX_TEXT).encode(buf, p.data().balanceText());
                    ByteBufCodecs.BOOL.encode(buf, p.data().hasItems());
                    str(MAX_TEXT).encode(buf, p.data().currencyName());
                    ByteBufCodecs.VAR_INT.encode(buf, p.data().buttons().size());
                    for (MarketViews.CurrencyButton b : p.data().buttons()) {
                        str(MAX_ID).encode(buf, b.itemId());
                        str(MAX_TEXT).encode(buf, b.name());
                        ByteBufCodecs.VAR_LONG.encode(buf, b.value());
                        ByteBufCodecs.VAR_INT.encode(buf, b.maxStack());
                    }
                },
                buf -> {
                    String balance = str(MAX_TEXT).decode(buf);
                    boolean hasItems = ByteBufCodecs.BOOL.decode(buf);
                    String name = str(MAX_TEXT).decode(buf);
                    int n = ByteBufCodecs.VAR_INT.decode(buf);
                    if (n < 0 || n > MAX_ROWS) {
                        throw new io.netty.handler.codec.DecoderException("Too many currency items: " + n);
                    }
                    List<MarketViews.CurrencyButton> buttons = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        buttons.add(new MarketViews.CurrencyButton(str(MAX_ID).decode(buf), str(MAX_TEXT).decode(buf),
                                ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
                    }
                    return new WalletPayload(new MarketViews.WalletView(balance, hasItems, name, buttons));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SellPreviewPayload(MarketViews.SellPreview data) implements CustomPacketPayload {
        public static final Type<SellPreviewPayload> TYPE = channelType("sell_preview");
        public static final StreamCodec<ByteBuf, SellPreviewPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    MarketViews.SellPreview d = p.data();
                    ByteBufCodecs.VAR_INT.encode(buf, d.status().ordinal());
                    str(MAX_TEXT).encode(buf, d.priceText());
                    str(MAX_TEXT).encode(buf, d.cutText());
                    str(MAX_TEXT).encode(buf, d.receiveText());
                    ByteBufCodecs.VAR_INT.encode(buf, d.cutPercent());
                    ByteBufCodecs.VAR_INT.encode(buf, d.limit());
                    str(MAX_PRICE_TEXT).encode(buf, d.asked());
                    ByteBufCodecs.INT.encode(buf, d.token());
                },
                buf -> {
                    int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
                    MarketViews.SellStatus[] all = MarketViews.SellStatus.values();
                    MarketViews.SellStatus status = ordinal >= 0 && ordinal < all.length ? all[ordinal]
                            : MarketViews.SellStatus.BAD_AMOUNT;
                    return new SellPreviewPayload(new MarketViews.SellPreview(status, str(MAX_TEXT).decode(buf),
                            str(MAX_TEXT).decode(buf), str(MAX_TEXT).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf), str(MAX_PRICE_TEXT).decode(buf), ByteBufCodecs.INT.decode(buf)));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The result of an action, as chat lines the server already built (translatable with English fallback). */
    public record ReplyPayload(boolean ok, List<Component> lines) implements CustomPacketPayload {
        public static final Type<ReplyPayload> TYPE = channelType("reply");
        public static final StreamCodec<RegistryFriendlyByteBuf, ReplyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReplyPayload::ok,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), ReplyPayload::lines,
                ReplyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
