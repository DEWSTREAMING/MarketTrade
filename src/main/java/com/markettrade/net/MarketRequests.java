package com.markettrade.net;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.market.AmountParser;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;
import com.markettrade.market.MarketViews;
import com.markettrade.market.RateLimiter;
import com.markettrade.market.StoredStack;
import com.markettrade.market.SummaryRow;
import com.markettrade.market.WalletActions;
import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the server does with a request from the client screen. It checks the sender, then calls {@link MarketViews} and the same
 * actions the chest screen uses. It sends through {@link Out}, so GameTests can run it with mock players and read the answers.
 * Everything arrives as a request: ids, prices and stacks are checked again against the market as it is now.
 */
public final class MarketRequests {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int REQUESTS_PER_SECOND = 20;
    public static final int SUMMARY_CHUNK = 300;
    private static final int MAX_PAGE = 100_000;
    private static final int MAX_WITHDRAW_COUNT = 4096;

    /** Where answers go. */
    public interface Out {
        void send(CustomPacketPayload payload);
    }

    private static final RateLimiter LIMITER = new RateLimiter(REQUESTS_PER_SECOND);

    private MarketRequests() {
    }

    /** The real connection. Nothing is sent to a client that doesn't have the channel. */
    public static Out connection(ServerPlayer player) {
        return payload -> {
            if (player.connection.hasChannel(payload.type())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        };
    }

    public static void forget(UUID player) {
        LIMITER.forget(player);
    }

    public static void resetLimits() {
        LIMITER.clear();
    }

    /** True only for a player who passed the hello check and is inside the rate limit. */
    private static boolean accept(ServerPlayer player, long now) {
        return ClientTracker.hasValidHello(player.getUUID()) && LIMITER.allow(player.getUUID(), now);
    }

    // Views

    public static void view(ServerPlayer player, MarketPackets.ViewRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        MarketPackets.ViewKind[] kinds = MarketPackets.ViewKind.values();
        if (request.kind() < 0 || request.kind() >= kinds.length) {
            return;
        }
        int page = Math.max(0, Math.min(request.page(), MAX_PAGE));
        int size = MarketViews.clampSize(request.pageSize());
        int generation = request.generation();
        safely(() -> {
            switch (kinds[request.kind()]) {
                case SUMMARY -> sendSummary(player, out, generation, now);
                case ITEM -> {
                    String id = request.text();
                    if (id == null || id.length() > MarketPackets.MAX_ID || ResourceLocation.tryParse(id) == null) {
                        return;
                    }
                    out.send(new MarketPackets.ItemPagePayload(generation,
                            MarketViews.itemPage(player, player.registryAccess(), id, page, size, now)));
                }
                case MINE -> out.send(new MarketPackets.MyListingsPayload(generation,
                        MarketViews.myListings(player, player.registryAccess(), page, size, now)));
                case RETURNS -> out.send(new MarketPackets.ReturnsPayload(generation,
                        MarketViews.returns(player, player.registryAccess(), page, size)));
                case WALLET -> out.send(new MarketPackets.WalletPayload(MarketViews.wallet(player)));
                case STATUS -> {
                }
            }
            sendStatus(player, out);
        });
    }

    private static void sendSummary(ServerPlayer player, Out out, int generation, long now) {
        List<SummaryRow> rows = MarketViews.summary(player.server, now);
        if (rows.isEmpty()) {
            out.send(new MarketPackets.SummaryChunk(generation, 0, true, List.of()));
            return;
        }
        for (int from = 0; from < rows.size(); from += SUMMARY_CHUNK) {
            int to = Math.min(rows.size(), from + SUMMARY_CHUNK);
            out.send(new MarketPackets.SummaryChunk(generation, rows.size(), from == 0, rows.subList(from, to)));
        }
    }

    private static void sendStatus(ServerPlayer player, Out out) {
        out.send(new MarketPackets.StatusPayload(MarketViews.status(player)));
    }

    // Actions

    public static void buy(ServerPlayer player, MarketPackets.BuyRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        MarketActions.BuyResult result = MarketActions.buy(player, request.id(), request.price(), now);
        reply(out, () -> Replies.buy(result));
        safely(() -> sendStatus(player, out));
    }

    public static void sell(ServerPlayer player, MarketPackets.SellRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        String priceText = request.priceText();
        if (priceText == null || priceText.length() > MarketPackets.MAX_PRICE_TEXT) {
            priceText = "";
        }
        if (!request.confirm()) {
            MarketViews.SellPreview preview = MarketViews.sellPreview(player, request.slot(), request.count(), request.token(),
                    priceText);
            safely(() -> out.send(new MarketPackets.SellPreviewPayload(preview)));
            return;
        }
        AmountParser.Result parsed = AmountParser.parse(priceText, Currencies.current());
        if (!parsed.ok()) {
            out.send(new MarketPackets.ReplyPayload(false, List.of(Replies.amountProblem(parsed.status()))));
            return;
        }
        int slot = request.slot();
        ItemStack shown = slot >= 0 && slot < 36 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        ItemStack listed = shown.copy();
        // A stack that doesn't match what the player saw is passed on as "nothing", which the action reads as changed.
        ItemStack expected = MarketViews.matchesToken(shown, request.count(), request.token()) ? shown.copy() : ItemStack.EMPTY;
        MarketActions.SellResult result = MarketActions.sell(player, slot, expected, parsed.amount(), now);
        reply(out, () -> Replies.sell(result, listed, parsed.amount()));
        safely(() -> sendStatus(player, out));
    }

    public static void cancel(ServerPlayer player, MarketPackets.CancelRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        MarketActions.CancelResult result = MarketActions.cancel(player, request.id());
        reply(out, () -> Replies.cancel(result, request.id()));
        safely(() -> sendStatus(player, out));
    }

    public static void claim(ServerPlayer player, MarketPackets.ClaimRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        if (request.index() == -1) {
            MarketActions.ClaimResult result = MarketActions.claimReturns(player);
            reply(out, () -> Replies.claimAll(result));
        } else {
            List<StoredStack> entries = MarketData.get(player.server).returnsOf(player.getUUID());
            MarketActions.ClaimOneResult result;
            int index = request.index();
            if (index < 0 || index >= entries.size()) {
                result = new MarketActions.ClaimOneResult(MarketActions.ClaimOneStatus.GONE, ItemStack.EMPTY, 0, 0);
            } else {
                StoredStack entry = entries.get(index);
                boolean same = entry.isAvailable() ? MarketViews.matchesToken(entry.stack(), request.count(), request.token())
                        : entry.count() == request.count();
                // Anything that isn't the stack the player saw is passed as an unrelated empty entry, so the action says GONE.
                StoredStack expected = same ? entry : StoredStack.unavailable(new CompoundTag());
                result = MarketActions.claimReturn(player, index, expected);
            }
            MarketActions.ClaimOneResult shown = result;
            reply(out, () -> Replies.claimOne(shown));
        }
        safely(() -> sendStatus(player, out));
    }

    public static void wallet(ServerPlayer player, MarketPackets.WalletRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        MarketPackets.WalletKind[] kinds = MarketPackets.WalletKind.values();
        if (request.kind() < 0 || request.kind() >= kinds.length) {
            return;
        }
        String itemId = request.itemId() == null ? "" : request.itemId();
        if (itemId.length() > MarketPackets.MAX_ID) {
            return;
        }
        switch (kinds[request.kind()]) {
            case DEPOSIT_ALL -> {
                WalletActions.DepositResult result = WalletActions.depositAll(player);
                reply(out, () -> Replies.deposit(result));
            }
            case WITHDRAW_ITEM -> {
                if (request.count() < 1 || request.count() > MAX_WITHDRAW_COUNT) {
                    return;
                }
                WalletActions.WithdrawResult result = WalletActions.withdrawItem(player, itemId, request.count());
                reply(out, () -> Replies.withdraw(result));
            }
            case WITHDRAW_STACK -> {
                WalletActions.WithdrawResult result = WalletActions.withdrawStack(player, itemId);
                reply(out, () -> Replies.withdraw(result));
            }
            case WITHDRAW_AMOUNT -> {
                String text = request.text() == null || request.text().length() > MarketPackets.MAX_PRICE_TEXT ? ""
                        : request.text();
                AmountParser.Result parsed = AmountParser.parse(text, Currencies.current());
                if (!parsed.ok()) {
                    out.send(new MarketPackets.ReplyPayload(false, List.of(Replies.amountProblem(parsed.status()))));
                    return;
                }
                WalletActions.WithdrawResult result = WalletActions.withdraw(player, parsed.amount());
                reply(out, () -> Replies.withdraw(result));
            }
        }
        safely(() -> {
            sendStatus(player, out);
            out.send(new MarketPackets.WalletPayload(MarketViews.wallet(player)));
        });
    }

    /** The open key: the same as running {@code /market}. */
    public static void open(ServerPlayer player, long now) {
        if (!accept(player, now)) {
            return;
        }
        com.markettrade.command.MarketCommand.openMarket(player);
    }

    public static void report(ServerPlayer player, MarketPackets.ReportRequest request, Out out, long now) {
        if (!accept(player, now)) {
            return;
        }
        String reason = request.reason() == null ? "" : request.reason();
        MarketActions.ReportResult result = MarketActions.report(player, request.id(), reason, now);
        reply(out, () -> Replies.report(result));
    }

    // Helpers

    /**
     * Builds the answer text and sends it. This runs after money or items may have moved, so a failure while reading an odd item
     * must not escape: the player gets a plain line instead.
     */
    private static void reply(Out out, Supplier<Replies.Reply> build) {
        try {
            Replies.Reply reply = build.get();
            out.send(new MarketPackets.ReplyPayload(reply.ok(), reply.lines()));
        } catch (RuntimeException e) {
            LOGGER.error("Could not build the answer to a request", e);
            try {
                out.send(new MarketPackets.ReplyPayload(true, List.of(Text.translatable("markettrade.reply_done"))));
            } catch (RuntimeException again) {
                LOGGER.error("Could not send the plain answer either", again);
            }
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("Could not answer a request from the market screen", e);
        }
    }
}
