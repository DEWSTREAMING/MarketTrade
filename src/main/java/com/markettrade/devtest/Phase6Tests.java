package com.markettrade.devtest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.market.AuditEntry;
import com.markettrade.market.AuditFiles;
import com.markettrade.market.AuditLog;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.MarketViews;
import com.markettrade.market.ShopBan;
import com.markettrade.market.WalletData;
import com.markettrade.net.ClientTracker;
import com.markettrade.net.MarketPackets;
import com.markettrade.net.MarketRequests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The client screen's requests, run through {@link MarketRequests} with mock players and a capturing {@code Out}. Each test has a
 * batch of its own because the market, the wallets and the rate limiter are shared by the whole test server.
 */
@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase6Tests {
    /** Collects what the server would send to one client. */
    private static final class Capture implements MarketRequests.Out {
        final List<CustomPacketPayload> sent = new ArrayList<>();

        @Override
        public void send(CustomPacketPayload payload) {
            sent.add(payload);
        }

        <T extends CustomPacketPayload> List<T> of(Class<T> type) {
            List<T> out = new ArrayList<>();
            for (CustomPacketPayload p : sent) {
                if (type.isInstance(p)) {
                    out.add(type.cast(p));
                }
            }
            return out;
        }

        MarketPackets.ReplyPayload lastReply() {
            List<MarketPackets.ReplyPayload> replies = of(MarketPackets.ReplyPayload.class);
            return replies.isEmpty() ? null : replies.get(replies.size() - 1);
        }
    }

    // Setup

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, boolean hello) {
        Currencies.set(CurrencyParser.defaultConfig());
        MarketConfigs.set(MarketConfig.defaults());
        MarketRequests.resetLimits();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        MarketData.get(player.server).replaceReturns(player.getUUID(), List.of());
        if (hello) {
            ClientTracker.recordHello(player.getUUID());
        }
        return player;
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        MarketData market = MarketData.get(helper.getLevel().getServer());
        for (ServerPlayer player : players) {
            ClientTracker.onLogout(player.getUUID());
            market.removeBan(player.getUUID());
            market.replaceReturns(player.getUUID(), List.of());
            WalletData.get(player.server).set(player.getUUID(), 0);
            player.getInventory().clearContent();
        }
        TestMarket.clearAll(helper.getLevel().getServer());
        MarketRequests.resetLimits();
        MarketConfigs.set(MarketConfig.defaults());
        helper.succeed();
    }

    private static long wallet(ServerPlayer player) {
        return WalletData.get(player.server).balance(player.getUUID());
    }

    private static long now() {
        return System.currentTimeMillis();
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

    private static MarketPackets.SellRequest sellRequest(ServerPlayer player, int slot, String price, boolean confirm) {
        ItemStack stack = player.getInventory().getItem(slot);
        int token = confirm ? MarketViews.token(stack) : BuiltInRegistries.ITEM.getId(stack.getItem());
        return new MarketPackets.SellRequest(slot, stack.getCount(), token, price, confirm);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value, MinecraftServer server, int[] size) {
        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, server.registryAccess());
        codec.encode(buf, value);
        size[0] = raw.readableBytes();
        T back = codec.decode(buf);
        raw.release();
        return back;
    }

    // Tests

    @GameTest(template = "empty", batch = "p6-nohello")
    public static void aRequestFromAPlayerWithNoHelloDoesNothing(GameTestHelper helper) {
        ServerPlayer seller = player(helper, false);
        ServerPlayer buyer = player(helper, false);
        long id = TestMarket.add(seller, new ItemStack(Items.DIAMOND_PICKAXE), 10);
        WalletData.get(buyer.server).set(buyer.getUUID(), 50);
        buyer.getInventory().setItem(0, new ItemStack(Items.STICK));
        Capture out = new Capture();
        long t = now();

        MarketRequests.view(buyer, new MarketPackets.ViewRequest(0, 0, 8, "", 1), out, t);
        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(id, 10), out, t);
        MarketRequests.sell(buyer, sellRequest(buyer, 0, "5", true), out, t);
        MarketRequests.cancel(seller, new MarketPackets.CancelRequest(id), out, t);
        MarketRequests.claim(buyer, new MarketPackets.ClaimRequest(-1, 0, 0), out, t);
        MarketRequests.wallet(buyer, new MarketPackets.WalletRequest(0, "", 0, ""), out, t);
        MarketRequests.report(buyer, new MarketPackets.ReportRequest(id, "x"), out, t);

        helper.assertTrue(out.sent.isEmpty(), "Nothing may be answered without a hello, got " + out.sent.size());
        helper.assertTrue(MarketData.get(buyer.server).listing(id) != null, "The listing is still there");
        helper.assertTrue(wallet(buyer) == 50, "The buyer's wallet is untouched");
        helper.assertTrue(buyer.getInventory().getItem(0).is(Items.STICK), "The buyer's stack is untouched");
        helper.assertTrue(MarketData.get(buyer.server).countListingsOf(buyer.getUUID()) == 0, "Nothing was listed");
        finish(helper, seller, buyer);
    }

    @GameTest(template = "empty", batch = "p6-openkey")
    public static void theOpenKeyOpensTheMarketLikeTheCommand(GameTestHelper helper) {
        ServerPlayer stranger = player(helper, false);
        MarketRequests.open(stranger, now());
        helper.assertTrue(!(stranger.containerMenu instanceof com.markettrade.gui.MarketChestMenu),
                "No hello, nothing opens");
        ServerPlayer p = player(helper, true);
        MarketRequests.open(p, now());
        // A mock player has no channel, so the server falls back to the chest screen, as for a client without the mod.
        helper.assertTrue(p.containerMenu instanceof com.markettrade.gui.MarketChestMenu, "The key opens the market");
        p.closeContainer();
        finish(helper, stranger, p);
    }

    @GameTest(template = "empty", batch = "p6-buyrefusals")
    public static void buyingWithAWrongPriceASoldListingOrWhileBannedChangesNothing(GameTestHelper helper) {
        ServerPlayer seller = player(helper, true);
        ServerPlayer buyer = player(helper, true);
        MarketData market = MarketData.get(buyer.server);
        long id = TestMarket.add(seller, fancySword(helper), 10);
        WalletData.get(buyer.server).set(buyer.getUUID(), 50);
        Capture out = new Capture();

        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(id, 9), out, now());
        helper.assertTrue(!out.lastReply().ok(), "A wrong price is refused");
        helper.assertTrue(market.listing(id) != null && wallet(buyer) == 50 && wallet(seller) == 0, "Nothing moved");

        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(id + 1000, 10), out, now());
        helper.assertTrue(!out.lastReply().ok(), "A listing that does not exist is refused");
        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(-4, 10), out, now());
        helper.assertTrue(!out.lastReply().ok(), "A negative id is refused");

        market.addBan(new ShopBan(buyer.getUUID(), "buyer", null, "admin", "test", 1));
        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(id, 10), out, now());
        helper.assertTrue(!out.lastReply().ok(), "A banned player is refused");
        helper.assertTrue(market.listing(id) != null && wallet(buyer) == 50 && wallet(seller) == 0
                && buyer.getInventory().getItem(0).isEmpty(), "Still nothing moved");
        market.removeBan(buyer.getUUID());

        MarketRequests.buy(seller, new MarketPackets.BuyRequest(id, 10), out, now());
        helper.assertTrue(market.listing(id) != null, "Buying your own listing changes nothing");

        // Sold: a second buyer takes it first.
        ServerPlayer other = player(helper, true);
        WalletData.get(buyer.server).set(other.getUUID(), 50);
        MarketRequests.buy(other, new MarketPackets.BuyRequest(id, 10), new Capture(), now());
        helper.assertTrue(market.listing(id) == null, "The first buyer got it");
        long afterFirst = wallet(buyer);
        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(id, 10), out, now());
        helper.assertTrue(!out.lastReply().ok() && wallet(buyer) == afterFirst && buyer.getInventory().getItem(0).isEmpty(),
                "A sold listing changes nothing for the second buyer");
        finish(helper, seller, buyer, other);
    }

    @GameTest(template = "empty", batch = "p6-sellchanged")
    public static void sellingWithAChangedStackOrBadSlotChangesNothing(GameTestHelper helper) {
        ServerPlayer seller = player(helper, true);
        MarketData market = MarketData.get(seller.server);
        ItemStack sword = fancySword(helper);
        seller.getInventory().setItem(0, sword.copy());
        Capture out = new Capture();
        MarketPackets.SellRequest seen = sellRequest(seller, 0, "5", true);

        // The stack changes between the screen and the click.
        seller.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        MarketRequests.sell(seller, seen, out, now());
        helper.assertTrue(!out.lastReply().ok(), "A changed stack is refused");
        helper.assertTrue(market.countListingsOf(seller.getUUID()) == 0, "Nothing was listed");
        helper.assertTrue(ItemStack.matches(seller.getInventory().getItem(0), new ItemStack(Items.DIAMOND_SWORD)),
                "The stack stays where it was");

        // The count changes.
        seller.getInventory().setItem(0, sword.copy());
        MarketPackets.SellRequest oneSword = sellRequest(seller, 0, "5", true);
        seller.getInventory().getItem(0).setCount(1);
        MarketRequests.sell(seller, new MarketPackets.SellRequest(0, 2, oneSword.token(), "5", true), out, now());
        helper.assertTrue(market.countListingsOf(seller.getUUID()) == 0, "A different count is refused");

        for (int slot : new int[] { -1, 36, 40, 99, Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            MarketRequests.sell(seller, new MarketPackets.SellRequest(slot, 1, 0, "5", true), out, now());
            MarketRequests.sell(seller, new MarketPackets.SellRequest(slot, 1, 0, "5", false), out, now());
        }
        helper.assertTrue(market.countListingsOf(seller.getUUID()) == 0, "Bad slots list nothing");
        helper.assertTrue(ItemStack.matches(seller.getInventory().getItem(0), sword), "The sword is still in the slot");
        finish(helper, seller);
    }

    @GameTest(template = "empty", batch = "p6-badinput")
    public static void badPagesSlotsKindsAndLongStringsAreRefusedWithoutAnException(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        TestMarket.add(p, new ItemStack(Items.STICK), 3);
        p.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
        Capture out = new Capture();
        String longText = "a".repeat(5000);
        int[] pages = { -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE };

        for (int kind : new int[] { -1, 0, 1, 2, 3, 4, 5, 6, 99, Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            for (int page : pages) {
                MarketRequests.resetLimits();
                for (String text : new String[] { "", "minecraft:stick", longText, "NOT AN ID!!", "minecraft:" }) {
                    MarketRequests.view(p, new MarketPackets.ViewRequest(kind, page, page, text, page), out, now());
                }
            }
        }
        MarketRequests.resetLimits();
        MarketRequests.sell(p, new MarketPackets.SellRequest(0, 3, MarketViews.token(p.getInventory().getItem(0)), longText, true),
                out, now());
        MarketRequests.sell(p, new MarketPackets.SellRequest(0, 3, MarketViews.token(p.getInventory().getItem(0)), "9".repeat(60),
                true), out, now());
        MarketRequests.sell(p, new MarketPackets.SellRequest(0, 3, MarketViews.token(p.getInventory().getItem(0)), "-5", true),
                out, now());
        MarketRequests.claim(p, new MarketPackets.ClaimRequest(Integer.MAX_VALUE, 1, 1), out, now());
        MarketRequests.claim(p, new MarketPackets.ClaimRequest(-7, 1, 1), out, now());
        MarketRequests.claim(p, new MarketPackets.ClaimRequest(0, 1, 1), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(-1, "", 0, ""), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(1, longText, 1, ""), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(1, "minecraft:diamond", Integer.MAX_VALUE, ""), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(1, "minecraft:diamond", -3, ""), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(3, "", 0, longText), out, now());
        MarketRequests.wallet(p, new MarketPackets.WalletRequest(3, "", 0, "99999999999999999999999"), out, now());
        MarketRequests.report(p, new MarketPackets.ReportRequest(Long.MAX_VALUE, longText), out, now());
        MarketRequests.cancel(p, new MarketPackets.CancelRequest(Long.MIN_VALUE), out, now());
        MarketRequests.buy(p, new MarketPackets.BuyRequest(Long.MAX_VALUE, Long.MIN_VALUE), out, now());

        helper.assertTrue(MarketData.get(p.server).countListingsOf(p.getUUID()) == 1, "Only the seeded listing exists");
        helper.assertTrue(p.getInventory().getItem(0).getCount() == 3 && wallet(p) == 0, "Nothing moved");
        finish(helper, p);
    }

    @GameTest(template = "empty", batch = "p6-ratelimit")
    public static void theRateLimitDropsTheExtraRequests(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        Capture out = new Capture();
        long t = now();
        for (int i = 0; i < 100; i++) {
            MarketRequests.view(p, new MarketPackets.ViewRequest(5, 0, 8, "", i), out, t);
        }
        int answered = out.of(com.markettrade.net.MarketPackets.StatusPayload.class).size();
        helper.assertTrue(answered == MarketRequests.REQUESTS_PER_SECOND,
                "A burst of 100 gets " + MarketRequests.REQUESTS_PER_SECOND + " answers, got " + answered);
        MarketRequests.view(p, new MarketPackets.ViewRequest(5, 0, 8, "", 1), out, t + 2000);
        helper.assertTrue(out.of(com.markettrade.net.MarketPackets.StatusPayload.class).size() == answered + 1,
                "Two seconds later a request goes through again");
        finish(helper, p);
    }

    @GameTest(template = "empty", batch = "p6-fullsale")
    public static void aFullSaleThroughRequestsKeepsItemDataMoneyPoolAndAudit(GameTestHelper helper) {
        ServerPlayer seller = player(helper, true);
        ServerPlayer buyer = player(helper, true);
        MinecraftServer server = seller.server;
        MarketData market = MarketData.get(server);
        ItemStack sword = fancySword(helper);
        seller.getInventory().setItem(2, sword.copy());
        WalletData.get(server).set(buyer.getUUID(), 100);
        long pool = market.pool();
        Capture sellerOut = new Capture();
        Capture buyerOut = new Capture();

        MarketRequests.sell(seller, sellRequest(seller, 2, "4 diamond block 4 diamond", false), sellerOut, now());
        MarketViews.SellPreview preview = sellerOut.of(MarketPackets.SellPreviewPayload.class).get(0).data();
        helper.assertTrue(preview.status() == MarketViews.SellStatus.OK, "Preview is fine, got " + preview.status());
        helper.assertTrue(preview.receiveText().length() > 0 && preview.cutPercent() == 5, "Preview carries cut and payout");
        helper.assertTrue(!seller.getInventory().getItem(2).isEmpty(), "A preview takes nothing");

        // The real order: the confirm echoes the server's token, because a client can't work one out.
        MarketRequests.sell(seller, new MarketPackets.SellRequest(2, seller.getInventory().getItem(2).getCount(),
                preview.token(), preview.asked(), true), sellerOut, now());
        helper.assertTrue(sellerOut.lastReply().ok(), "The sale is listed");
        List<Listing> listed = market.listingsOf(seller.getUUID());
        helper.assertTrue(listed.size() == 1 && listed.get(0).price() == 40, "One listing at 40");
        helper.assertTrue(seller.getInventory().getItem(2).isEmpty(), "The item left the inventory");
        helper.assertTrue(ItemStack.matches(listed.get(0).item().stack(), sword), "The listing holds every bit of data");

        // The buyer sees it on the item page.
        MarketRequests.view(buyer, new MarketPackets.ViewRequest(1, 0, 8, "minecraft:diamond_sword", 7), buyerOut, now());
        MarketPackets.ItemPagePayload page = buyerOut.of(MarketPackets.ItemPagePayload.class).get(0);
        helper.assertTrue(page.generation() == 7 && page.page().rows().size() == 1, "One row on the page");
        MarketViews.ListingRow row = page.page().rows().get(0);
        helper.assertTrue(ItemStack.matches(row.stack(), sword) && !row.yours() && row.price() == 40, "Row holds the sword");

        MarketRequests.buy(buyer, new MarketPackets.BuyRequest(row.id(), row.price()), buyerOut, now());
        helper.assertTrue(buyerOut.lastReply().ok(), "The buy worked");
        helper.assertTrue(ItemStack.matches(buyer.getInventory().getItem(0), sword), "The buyer has the same sword");
        helper.assertTrue(wallet(buyer) == 60, "Buyer paid 40, wallet " + wallet(buyer));
        helper.assertTrue(wallet(seller) == 38, "Seller got 38, wallet " + wallet(seller));
        helper.assertTrue(market.pool() == pool + 2, "Pool gained the cut of 2");
        helper.assertTrue(market.listing(row.id()) == null, "The listing is gone");
        helper.assertTrue(!buyerOut.of(MarketPackets.StatusPayload.class).isEmpty()
                && buyerOut.of(MarketPackets.StatusPayload.class).get(buyerOut.of(MarketPackets.StatusPayload.class).size() - 1)
                        .status().balance() == 60, "A fresh status follows the buy");

        List<AuditEntry> sellerLines = AuditFiles.read(AuditLog.dir(server), e -> e.involves(seller.getUUID()), 0, 100,
                w -> { }).entries();
        helper.assertTrue(sellerLines.stream().anyMatch(e -> e.event().equals("LIST")), "LIST was audited");
        helper.assertTrue(sellerLines.stream().anyMatch(e -> e.event().equals("BUY") && e.price() == 40 && e.cut() == 2),
                "BUY was audited with price and cut");

        // Claiming and cancelling through requests.
        seller.getInventory().setItem(3, new ItemStack(Items.APPLE, 5));
        MarketRequests.sell(seller, sellRequest(seller, 3, "7", true), sellerOut, now());
        long second = market.listingsOf(seller.getUUID()).get(0).id();
        MarketRequests.cancel(seller, new MarketPackets.CancelRequest(second), sellerOut, now());
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 1, "Cancel put the apples in Returns");
        MarketViews.Returns returns = MarketViews.returns(seller, server.registryAccess(), 0, 8);
        MarketViews.ReturnRow r = returns.rows().get(0);
        MarketRequests.claim(seller, new MarketPackets.ClaimRequest(r.index(), r.count() + 1, r.token()), sellerOut, now());
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 1, "A claim with the wrong count is refused");
        MarketRequests.claim(seller, new MarketPackets.ClaimRequest(r.index(), r.count(), r.token()), sellerOut, now());
        helper.assertTrue(market.returnsOf(seller.getUUID()).isEmpty()
                && seller.getInventory().countItem(Items.APPLE) == 5, "The claim gave the apples back");
        finish(helper, seller, buyer);
    }

    @GameTest(template = "empty", batch = "p6-nonames")
    public static void summaryRowsHoldNoCustomNames(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("SecretNameXyz"));
        TestMarket.add(p, named, 30);
        TestMarket.add(p, new ItemStack(Items.DIAMOND_SWORD), 20);
        Capture out = new Capture();
        MarketRequests.view(p, new MarketPackets.ViewRequest(0, 0, 8, "", 1), out, now());
        List<MarketPackets.SummaryChunk> chunks = out.of(MarketPackets.SummaryChunk.class);
        helper.assertTrue(chunks.size() == 1 && chunks.get(0).rows().size() == 1, "One row for the sword");
        helper.assertTrue(chunks.get(0).rows().get(0).listed() == 2 && chunks.get(0).rows().get(0).lowest() == 20,
                "Two listed, lowest 20");

        ByteBuf raw = Unpooled.buffer();
        MarketPackets.SummaryChunk.CODEC.encode(raw, chunks.get(0));
        String bytes = raw.toString(StandardCharsets.ISO_8859_1);
        helper.assertTrue(!bytes.contains("SecretNameXyz"), "The custom name is nowhere in the packet");
        MarketPackets.SummaryChunk back = MarketPackets.SummaryChunk.CODEC.decode(raw);
        helper.assertTrue(back.rows().get(0).itemId().equals("minecraft:diamond_sword"), "The row decodes by id");
        finish(helper, p);
    }

    @GameTest(template = "empty", batch = "p6-chunks")
    public static void bigSummariesGoOutInChunks(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        int made = 0;
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (made >= 700) {
                break;
            }
            if (item != Items.AIR) {
                TestMarket.add(p, new ItemStack(item), 5 + made);
                made++;
            }
        }
        Capture out = new Capture();
        MarketRequests.view(p, new MarketPackets.ViewRequest(0, 0, 8, "", 3), out, now());
        List<MarketPackets.SummaryChunk> chunks = out.of(MarketPackets.SummaryChunk.class);
        int total = chunks.stream().mapToInt(c -> c.rows().size()).sum();
        helper.assertTrue(chunks.size() == 3, "700 rows go in three chunks, got " + chunks.size());
        helper.assertTrue(chunks.get(0).first() && !chunks.get(1).first() && total == 700 && chunks.get(0).total() == 700,
                "First flag and total are right");
        helper.assertTrue(chunks.stream().allMatch(c -> c.generation() == 3 && c.rows().size() <= MarketRequests.SUMMARY_CHUNK),
                "Each chunk is small and carries the request's generation");
        finish(helper, p);
    }

    private static ItemStack bookShulker(int books, int pages, int pageChars) {
        List<ItemStack> contents = new ArrayList<>();
        for (int b = 0; b < books; b++) {
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            List<Filterable<Component>> text = new ArrayList<>();
            for (int i = 0; i < pages; i++) {
                text.add(Filterable.passThrough(Component.literal(("page " + b + " " + i + " ").repeat(pageChars / 8))));
            }
            book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Book " + b), "Author", 0,
                    text, true));
            contents.add(book);
        }
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return box;
    }

    @GameTest(template = "empty", batch = "p6-bigpages", timeoutTicks = 400)
    public static void aPageOfShulkerBoxesFullOfWrittenBooksFitsThroughTheCodec(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        MinecraftServer server = p.server;
        // Ten boxes of 27 books with 100 pages each: several megabytes in all.
        for (int i = 0; i < 10; i++) {
            TestMarket.add(p, bookShulker(27, 100, 1000), 10 + i);
        }
        Capture out = new Capture();
        MarketRequests.view(p, new MarketPackets.ViewRequest(1, 0, 10, "minecraft:shulker_box", 1), out, now());
        MarketPackets.ItemPagePayload payload = out.of(MarketPackets.ItemPagePayload.class).get(0);
        int[] size = new int[1];
        MarketPackets.ItemPagePayload back = roundTrip(MarketPackets.ItemPagePayload.CODEC, payload, server, size);
        helper.assertTrue(size[0] < 700_000, "The page is small enough for one packet, was " + size[0] + " bytes");
        helper.assertTrue(back.page().rows().size() == 10, "All ten listings are still on the page");
        helper.assertTrue(back.page().rows().stream().allMatch(r -> r.detailsHidden() && r.stack().isEmpty() && r.id() > 0),
                "Too-big stacks are sent without data but keep their id and price");

        // A page that fits only partly: each box is about 90 kB.
        TestMarket.clearAll(server);
        int tenPages = MarketViews.encodedSize(bookShulker(1, 10, 1000), server.registryAccess());
        int pagesFor90k = Math.max(1, 10 * 90_000 / tenPages);
        for (int i = 0; i < 10; i++) {
            TestMarket.add(p, bookShulker(1, pagesFor90k, 1000), 10 + i);
        }
        Capture out2 = new Capture();
        MarketRequests.resetLimits();
        MarketRequests.view(p, new MarketPackets.ViewRequest(1, 0, 10, "minecraft:shulker_box", 2), out2, now());
        MarketPackets.ItemPagePayload mixed = roundTrip(MarketPackets.ItemPagePayload.CODEC,
                out2.of(MarketPackets.ItemPagePayload.class).get(0), server, size);
        long shown = mixed.page().rows().stream().filter(r -> !r.stack().isEmpty()).count();
        helper.assertTrue(size[0] < 700_000, "The mixed page is under the budget, was " + size[0]);
        helper.assertTrue(shown >= 1 && shown < 10, "Some rows keep their data and the rest drop it, shown " + shown);
        helper.assertTrue(mixed.page().rows().size() == 10, "Every listing is on the page");

        // My Listings and Returns share the budget rule.
        Capture out3 = new Capture();
        MarketRequests.resetLimits();
        MarketRequests.view(p, new MarketPackets.ViewRequest(2, 0, 12, "", 3), out3, now());
        MarketPackets.MyListingsPayload mine = roundTrip(MarketPackets.MyListingsPayload.CODEC,
                out3.of(MarketPackets.MyListingsPayload.class).get(0), server, size);
        helper.assertTrue(size[0] < 700_000 && mine.data().rows().size() == 10, "My Listings fits too, " + size[0]);
        finish(helper, p);
    }

    @GameTest(template = "empty", batch = "p6-views")
    public static void everyViewRoundTripsThroughItsCodec(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        MinecraftServer server = p.server;
        TestMarket.add(p, fancySword(helper), 40);
        MarketData.get(server).addReturn(p.getUUID(), com.markettrade.market.StoredStack.of(fancySword(helper)));
        WalletData.get(server).set(p.getUUID(), 25);
        Capture out = new Capture();
        long t = now();
        MarketRequests.view(p, new MarketPackets.ViewRequest(1, 0, 8, "minecraft:diamond_sword", 1), out, t);
        MarketRequests.view(p, new MarketPackets.ViewRequest(2, 0, 8, "", 1), out, t);
        MarketRequests.view(p, new MarketPackets.ViewRequest(3, 0, 8, "", 1), out, t);
        MarketRequests.view(p, new MarketPackets.ViewRequest(4, 0, 8, "", 1), out, t);
        int[] size = new int[1];

        MarketViews.ItemPage item = roundTrip(MarketPackets.ItemPagePayload.CODEC,
                out.of(MarketPackets.ItemPagePayload.class).get(0), server, size).page();
        helper.assertTrue(item.rows().size() == 1 && item.rows().get(0).yours() && item.chart().length == 14
                && item.chart()[0] == -1, "Item page decodes, my own listing is marked yours");
        helper.assertTrue(item.rows().get(0).priceText().equals("40 diamond") || item.rows().get(0).priceText().length() > 0,
                "Price text is formatted by the server");
        MarketViews.Returns returns = roundTrip(MarketPackets.ReturnsPayload.CODEC,
                out.of(MarketPackets.ReturnsPayload.class).get(0), server, size).data();
        helper.assertTrue(returns.rows().size() == 1 && ItemStack.matches(returns.rows().get(0).stack(), fancySword(helper)),
                "Returns decode with the full stack");
        MarketViews.WalletView wallet = roundTrip2(out.of(MarketPackets.WalletPayload.class).get(0).data(), server);
        helper.assertTrue(wallet.hasItems() && wallet.buttons().size() == 2, "Wallet has the two currency items");
        MarketViews.Status status = out.of(MarketPackets.StatusPayload.class).get(0).status();
        helper.assertTrue(status.balance() == 25 && status.myListings() == 1 && status.returns() == 1, "Status counts");

        MarketViews.SellPreview bad = MarketViews.sellPreview(p, 0, 1, 0, "");
        helper.assertTrue(bad.status() == MarketViews.SellStatus.EMPTY_SLOT, "Empty slot preview");
        p.getInventory().setItem(0, new ItemStack(Items.APPLE));
        int apple = BuiltInRegistries.ITEM.getId(Items.APPLE);
        helper.assertTrue(MarketViews.sellPreview(p, 0, 1, apple, "abc").status() == MarketViews.SellStatus.BAD_AMOUNT,
                "Bad amount preview");
        helper.assertTrue(MarketViews.sellPreview(p, 0, 1, apple, "").status() == MarketViews.SellStatus.EMPTY_TEXT,
                "Empty text preview");
        helper.assertTrue(MarketViews.sellPreview(p, 0, 1, 12345, "5").status() == MarketViews.SellStatus.CHANGED,
                "Wrong item preview");
        MarketViews.SellPreview good = MarketViews.sellPreview(p, 0, 1, apple, "5");
        helper.assertTrue(good.status() == MarketViews.SellStatus.OK && good.asked().equals("5"), "Good preview");
        helper.assertTrue(good.token() == MarketViews.token(p.getInventory().getItem(0)), "The token comes from the server");
        finish(helper, p);
    }

    private static MarketViews.WalletView roundTrip2(MarketViews.WalletView view, MinecraftServer server) {
        ByteBuf raw = Unpooled.buffer();
        MarketPackets.WalletPayload.CODEC.encode(raw, new MarketPackets.WalletPayload(view));
        MarketViews.WalletView back = MarketPackets.WalletPayload.CODEC.decode(raw).data();
        raw.release();
        return back;
    }

    @GameTest(template = "empty", batch = "p6-unavailable")
    public static void anUnavailableItemIsSentByIdOnly(GameTestHelper helper) {
        ServerPlayer p = player(helper, true);
        MinecraftServer server = p.server;
        net.minecraft.nbt.CompoundTag ghost = new net.minecraft.nbt.CompoundTag();
        ghost.putString("id", "ghostmod:ghost_item");
        ghost.putInt("count", 2);
        MarketData market = MarketData.get(server);
        long id = market.nextId();
        market.addListing(new Listing(id, p.getUUID(), "someone", com.markettrade.market.StoredStack.unavailable(ghost), 15,
                now() + 100_000));
        Capture out = new Capture();
        MarketRequests.view(p, new MarketPackets.ViewRequest(0, 0, 8, "", 1), out, now());
        MarketRequests.view(p, new MarketPackets.ViewRequest(1, 0, 8, "ghostmod:ghost_item", 1), out, now());
        com.markettrade.market.SummaryRow row = out.of(MarketPackets.SummaryChunk.class).get(0).rows().get(0);
        helper.assertTrue(row.itemId().equals("ghostmod:ghost_item") && !row.available(), "Summary row names the id");
        MarketViews.ListingRow listing = out.of(MarketPackets.ItemPagePayload.class).get(0).page().rows().get(0);
        helper.assertTrue(!listing.available() && listing.stack().isEmpty() && listing.itemId().equals("ghostmod:ghost_item"),
                "The page row has an id and no stack");
        MarketRequests.buy(p, new MarketPackets.BuyRequest(id, 15), out, now());
        helper.assertTrue(market.listing(id) != null, "It can't be bought, and it is not deleted");
        market.removeListing(id);
        finish(helper, p);
    }
}
