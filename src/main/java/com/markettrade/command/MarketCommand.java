package com.markettrade.command;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.gui.MarketChestMenu;
import com.markettrade.market.Actor;
import com.markettrade.market.AuditEntry;
import com.markettrade.market.AuditFiles;
import com.markettrade.market.AuditSearch;
import com.markettrade.market.ItemTexts;
import com.markettrade.market.Listing;
import com.markettrade.market.Report;
import com.markettrade.market.ShopBan;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.Pagination;
import com.markettrade.market.PoolEntry;
import com.markettrade.market.TopSellers;
import com.markettrade.market.StoredStack;
import com.markettrade.market.WalletActions;
import com.markettrade.net.ClientTracker;
import com.markettrade.net.MarketPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class MarketCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MarketCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("market"));
        dispatcher.register(root("markettrade"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> true)
                .executes(MarketCommand::execute)
                .then(Commands.literal("chest").executes(ctx -> playerOnly(ctx, MarketCommand::openChest)))
                .then(Commands.literal("balance").executes(ctx -> playerOnly(ctx, MarketCommand::balance)))
                .then(Commands.literal("deposit")
                        .executes(ctx -> playerOnly(ctx, p -> deposit(ctx.getSource(), WalletActions.depositHand(p))))
                        .then(Commands.literal("all").executes(
                                ctx -> playerOnly(ctx, p -> deposit(ctx.getSource(), WalletActions.depositAll(p))))))
                .then(Commands.literal("withdraw")
                        .then(Commands.literal("all").executes(
                                ctx -> playerOnly(ctx, p -> withdraw(ctx.getSource(), WalletActions.withdrawAll(p)))))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(
                                ctx -> playerOnly(ctx, p -> withdraw(ctx.getSource(),
                                        WalletActions.withdraw(p, LongArgumentType.getLong(ctx, "amount")))))))
                .then(Commands.literal("sell").then(Commands.argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> playerOnly(ctx,
                                p -> sell(ctx.getSource(), p, LongArgumentType.getLong(ctx, "price"))))))
                .then(Commands.literal("buy").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .then(Commands.argument("price", LongArgumentType.longArg(1)).executes(
                                ctx -> playerOnly(ctx, p -> buy(ctx.getSource(), p, LongArgumentType.getLong(ctx, "id"),
                                        LongArgumentType.getLong(ctx, "price")))))))
                .then(Commands.literal("cancel").then(Commands.argument("id", LongArgumentType.longArg(1)).executes(
                        ctx -> playerOnly(ctx, p -> cancel(ctx.getSource(), p, LongArgumentType.getLong(ctx, "id"))))))
                .then(Commands.literal("listings")
                        .executes(ctx -> listings(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> listings(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("mine").executes(ctx -> playerOnly(ctx, p -> mine(ctx.getSource(), p))))
                .then(Commands.literal("returns")
                        .executes(ctx -> playerOnly(ctx, p -> returns(ctx.getSource(), p)))
                        .then(Commands.literal("claim")
                                .executes(ctx -> playerOnly(ctx, p -> claim(ctx.getSource(), p)))))
                .then(Commands.literal("report").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(ctx -> playerOnly(ctx, p -> report(ctx.getSource(), p,
                                LongArgumentType.getLong(ctx, "id"), "")))
                        .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(
                                ctx -> playerOnly(ctx, p -> report(ctx.getSource(), p, LongArgumentType.getLong(ctx, "id"),
                                        StringArgumentType.getString(ctx, "reason")))))))
                .then(admin());
    }

    private static int report(CommandSourceStack source, ServerPlayer player, long id, String reason) {
        return send(source, Replies.report(MarketActions.report(player, id, reason, System.currentTimeMillis())));
    }

    private static final int PAGE_SIZE = 10;

    private static Component nameOf(StoredStack stored) {
        return stored.isAvailable() ? stored.stack().getHoverName() : Component.literal(stored.itemId());
    }

    private static int send(CommandSourceStack source, Replies.Reply reply) {
        for (Component line : reply.lines()) {
            if (reply.ok()) {
                source.sendSuccess(() -> line, false);
            } else {
                source.sendFailure(line);
            }
        }
        return reply.ok() ? 1 : 0;
    }

    private static int sell(CommandSourceStack source, ServerPlayer player, long price) {
        Inventory inventory = player.getInventory();
        int slot = inventory.selected;
        ItemStack held = inventory.getItem(slot).copy();
        MarketActions.SellResult result = MarketActions.sell(player, slot, held, price, System.currentTimeMillis());
        return send(source, Replies.sell(result, held, price));
    }

    private static int buy(CommandSourceStack source, ServerPlayer player, long id, long price) {
        return send(source, Replies.buy(MarketActions.buy(player, id, price, System.currentTimeMillis())));
    }

    private static int cancel(CommandSourceStack source, ServerPlayer player, long id) {
        return send(source, Replies.cancel(MarketActions.cancel(player, id), id));
    }

    private static Component listingLine(Listing listing) {
        String key = listing.item().isAvailable() ? "markettrade.listing_line" : "markettrade.listing_line_unavailable";
        return Text.translatable(key, listing.id(), nameOf(listing.item()), listing.item().count(),
                Currencies.current().formatWithTotal(listing.price()), listing.sellerName());
    }

    private static int listings(CommandSourceStack source, int page) {
        List<Listing> all = MarketData.get(source.getServer()).listingsNewestFirst();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.no_listings"), false);
            return 1;
        }
        int pages = Pagination.totalPages(all.size(), PAGE_SIZE);
        int index = Pagination.clampPage(page - 1, pages);
        source.sendSuccess(() -> Text.translatable("markettrade.listings_header", index + 1, pages), false);
        int end = Pagination.endIndex(index, PAGE_SIZE, all.size());
        for (Listing listing : all.subList(Pagination.startIndex(index, PAGE_SIZE), end)) {
            source.sendSuccess(() -> listingLine(listing), false);
        }
        return 1;
    }

    private static int mine(CommandSourceStack source, ServerPlayer player) {
        List<Listing> own = MarketData.get(player.server).listingsOf(player.getUUID());
        if (own.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.mine_none"), false);
            return 1;
        }
        source.sendSuccess(() -> Text.translatable("markettrade.mine_header", own.size()), false);
        for (Listing listing : own) {
            source.sendSuccess(() -> listingLine(listing), false);
        }
        return 1;
    }

    private static int returns(CommandSourceStack source, ServerPlayer player) {
        List<StoredStack> stacks = MarketData.get(player.server).returnsOf(player.getUUID());
        if (stacks.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.returns_none"), false);
            return 1;
        }
        source.sendSuccess(() -> Text.translatable("markettrade.returns_header", stacks.size()), false);
        for (StoredStack stored : stacks) {
            String key = stored.isAvailable() ? "markettrade.returns_line" : "markettrade.returns_line_unavailable";
            source.sendSuccess(() -> Text.translatable(key, nameOf(stored), stored.count()), false);
        }
        source.sendSuccess(() -> Text.translatable("markettrade.returns_hint"), false);
        return 1;
    }

    private static int claim(CommandSourceStack source, ServerPlayer player) {
        if (MarketActions.returnsCount(player.server, player.getUUID()) == 0) {
            source.sendSuccess(() -> Text.translatable("markettrade.returns_none"), false);
            return 1;
        }
        return send(source, Replies.claimAll(MarketActions.claimReturns(player)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> adminChange(ctx, "give")))))
                .then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> adminChange(ctx, "take")))))
                .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> adminChange(ctx, "set")))))
                .then(Commands.literal("balance").then(Commands.argument("player", EntityArgument.player())
                        .executes(MarketCommand::adminBalance)))
                .then(Commands.literal("remove").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(MarketCommand::adminRemove)))
                .then(Commands.literal("pool").executes(MarketCommand::adminPool)
                        .then(Commands.literal("history").executes(ctx -> poolHistory(ctx.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> poolHistory(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("top").executes(ctx -> poolTop(ctx.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> poolTop(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("pay").then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(MarketCommand::poolPay))))
                        .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> poolSet(ctx, ""))
                                .then(Commands.argument("note", StringArgumentType.greedyString())
                                        .executes(ctx -> poolSet(ctx, StringArgumentType.getString(ctx, "note")))))))
                .then(Commands.literal("reload").executes(MarketCommand::reload))
                .then(Commands.literal("ban").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> ban(ctx, ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "reason"))))))
                .then(Commands.literal("unban").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(MarketCommand::unban)))
                .then(Commands.literal("bans").executes(ctx -> bans(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> bans(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("reports").executes(ctx -> reports(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(
                                ctx -> reports(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("report").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(ctx -> reportDetail(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))
                        .then(Commands.literal("close")
                                .executes(ctx -> reportClose(ctx.getSource(), LongArgumentType.getLong(ctx, "id"))))))
                .then(Commands.literal("history").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> history(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("find").then(Commands.argument("text", StringArgumentType.string())
                        .executes(ctx -> find(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> find(ctx, IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    private static int playerOnly(CommandContext<CommandSourceStack> context, ToIntFunction<ServerPlayer> action) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Text.translatable("markettrade.command_players_only"));
            return 0;
        }
        return action.applyAsInt(player);
    }

    private static String show(long amount) {
        return Currencies.current().format(amount);
    }

    private static int balance(ServerPlayer player) {
        long balance = WalletActions.balance(player.server, player.getUUID());
        player.sendSystemMessage(Text.translatable("markettrade.balance", Currencies.current().formatWithTotal(balance)));
        return 1;
    }

    private static int deposit(CommandSourceStack source, WalletActions.DepositResult result) {
        return send(source, Replies.deposit(result));
    }

    private static int withdraw(CommandSourceStack source, WalletActions.WithdrawResult result) {
        return send(source, Replies.withdraw(result));
    }

    private static int adminChange(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        Actor actor = actorOf(source);
        String targetName = target.getGameProfile().getName();
        WalletActions.AdminResult result = switch (action) {
            case "give" -> WalletActions.adminGive(source.getServer(), actor, target.getUUID(), targetName, amount);
            case "take" -> WalletActions.adminTake(source.getServer(), actor, target.getUUID(), targetName, amount);
            default -> WalletActions.adminSet(source.getServer(), actor, target.getUUID(), targetName, amount);
        };
        switch (result.status()) {
            case OK -> {
                source.sendSuccess(() -> Text.translatable("markettrade.admin_" + action, show(amount), targetName,
                        show(result.balance())), true);
                return 1;
            }
            case OVERFLOW -> source.sendFailure(Text.translatable("markettrade.admin_overflow", targetName));
            case INSUFFICIENT -> source.sendFailure(
                    Text.translatable("markettrade.admin_insufficient", targetName, show(result.balance())));
            case INVALID -> source.sendFailure(Text.translatable("markettrade.admin_invalid"));
        }
        return 0;
    }

    private static int adminBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long balance = WalletActions.balance(source.getServer(), target.getUUID());
        source.sendSuccess(() -> Text.translatable("markettrade.admin_balance", target.getGameProfile().getName(),
                show(balance)), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int problems = Currencies.load();
        String name = Currencies.current().name();
        if (problems == 0) {
            source.sendSuccess(() -> Text.translatable("markettrade.reload_ok", name), true);
        } else {
            source.sendSuccess(() -> Text.translatable("markettrade.reload_problems", name, problems), true);
        }
        int marketProblems = MarketConfigs.load();
        if (marketProblems == 0) {
            source.sendSuccess(() -> Text.translatable("markettrade.reload_market_ok"), true);
        } else {
            source.sendSuccess(() -> Text.translatable("markettrade.reload_market_problems", marketProblems), true);
        }
        return 1;
    }

    private static int adminRemove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "id");
        if (MarketActions.adminRemove(source.getServer(), actorOf(source), id) == MarketActions.RemoveStatus.OK) {
            source.sendSuccess(() -> Text.translatable("markettrade.admin_remove_ok", id), true);
            return 1;
        }
        source.sendFailure(Text.translatable("markettrade.admin_remove_not_found"));
        return 0;
    }

    private static Actor actorOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return new Actor(player == null ? null : player.getUUID(), source.getTextName());
    }

    /** Exactly one profile, offline players included. Says so and returns null otherwise. */
    private static GameProfile oneProfile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            context.getSource().sendFailure(Text.translatable("markettrade.pick_one_player"));
            return null;
        }
        return profiles.iterator().next();
    }

    private static int ban(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = oneProfile(context);
        if (profile == null) {
            return 0;
        }
        MarketActions.BanResult result = MarketActions.shopBan(source.getServer(), actorOf(source), profile.getId(),
                profile.getName(), reason);
        if (result.status() == MarketActions.BanStatus.OK) {
            source.sendSuccess(() -> Text.translatable("markettrade.ban_ok", profile.getName(), result.moved()), true);
            return 1;
        }
        source.sendFailure(Text.translatable("markettrade.ban_already", profile.getName()));
        return 0;
    }

    private static int unban(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = oneProfile(context);
        if (profile == null) {
            return 0;
        }
        MarketActions.BanResult result = MarketActions.shopUnban(source.getServer(), actorOf(source), profile.getId(),
                profile.getName());
        if (result.status() == MarketActions.BanStatus.OK) {
            source.sendSuccess(() -> Text.translatable("markettrade.unban_ok", profile.getName()), true);
            return 1;
        }
        source.sendFailure(Text.translatable("markettrade.unban_none", profile.getName()));
        return 0;
    }

    private static int bans(CommandSourceStack source, int page) {
        List<ShopBan> all = MarketData.get(source.getServer()).bansNewestFirst();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.bans_none"), false);
            return 1;
        }
        int pages = Pagination.totalPages(all.size(), PAGE_SIZE);
        int index = Pagination.clampPage(page - 1, pages);
        source.sendSuccess(() -> Text.translatable("markettrade.bans_header", index + 1, pages), false);
        for (ShopBan ban : all.subList(Pagination.startIndex(index, PAGE_SIZE),
                Pagination.endIndex(index, PAGE_SIZE, all.size()))) {
            String why = ban.reason().isEmpty() ? Text.translatable("markettrade.ban_no_reason").getString() : ban.reason();
            source.sendSuccess(() -> Text.translatable("markettrade.ban_line", ban.name(), ban.bannedByName(), why), false);
        }
        return 1;
    }

    private static String itemNameOf(StoredStack stored) {
        return nameOf(stored).getString();
    }

    private static int reports(CommandSourceStack source, int page) {
        List<Report> all = MarketData.get(source.getServer()).openReportsNewestFirst();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.reports_none"), false);
            return 1;
        }
        int pages = Pagination.totalPages(all.size(), PAGE_SIZE);
        int index = Pagination.clampPage(page - 1, pages);
        source.sendSuccess(() -> Text.translatable("markettrade.reports_header", index + 1, pages), false);
        for (Report report : all.subList(Pagination.startIndex(index, PAGE_SIZE),
                Pagination.endIndex(index, PAGE_SIZE, all.size()))) {
            source.sendSuccess(() -> Text.translatable("markettrade.reports_line", report.id(), report.listingId(),
                    report.sellerName(), itemNameOf(report.item()), report.item().count(), report.reporterName()), false);
        }
        return 1;
    }

    private static final int MAX_SHOWN_TEXTS = 12;
    private static final int MAX_SHOWN_TEXT_LENGTH = 120;

    private static int reportDetail(CommandSourceStack source, long id) {
        Report report = MarketData.get(source.getServer()).report(id);
        if (report == null) {
            source.sendFailure(Text.translatable("markettrade.report_admin_not_found"));
            return 0;
        }
        String state = Text.translatable(report.open() ? "markettrade.report_detail_open"
                : "markettrade.report_detail_closed").getString();
        source.sendSuccess(() -> Text.translatable("markettrade.report_detail_header", report.id(), state), false);
        source.sendSuccess(() -> Text.translatable("markettrade.report_detail_who", report.reporterName(),
                report.sellerName(), report.listingId(), TIME.format(Instant.ofEpochMilli(report.time()))), false);
        if (!report.reason().isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.report_detail_reason", report.reason()), false);
        }
        source.sendSuccess(() -> Text.translatable("markettrade.report_detail_item", itemNameOf(report.item()),
                report.item().count()), false);
        List<String> texts = report.item().isAvailable() ? ItemTexts.collect(report.item().stack()) : List.of();
        if (texts.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.report_detail_no_texts"), false);
        } else {
            source.sendSuccess(() -> Text.translatable("markettrade.report_detail_texts", texts.size()), false);
            for (int i = 0; i < Math.min(texts.size(), MAX_SHOWN_TEXTS); i++) {
                String text = shorten(texts.get(i));
                source.sendSuccess(() -> Text.translatable("markettrade.report_detail_text_line", text), false);
            }
        }
        return 1;
    }

    private static String shorten(String text) {
        String flat = text.replaceAll("\\p{Cntrl}", " ");
        return flat.length() > MAX_SHOWN_TEXT_LENGTH ? flat.substring(0, MAX_SHOWN_TEXT_LENGTH) + "..." : flat;
    }

    private static int reportClose(CommandSourceStack source, long id) {
        switch (MarketActions.closeReport(source.getServer(), actorOf(source), id)) {
            case OK -> {
                source.sendSuccess(() -> Text.translatable("markettrade.report_close_ok", id), true);
                return 1;
            }
            case ALREADY_CLOSED -> source.sendFailure(Text.translatable("markettrade.report_close_already"));
            case NOT_FOUND -> source.sendFailure(Text.translatable("markettrade.report_admin_not_found"));
        }
        return 0;
    }

    private static int history(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        GameProfile profile = oneProfile(context);
        if (profile == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Text.translatable("markettrade.history_searching"), false);
        AuditSearch.history(source.getServer(), profile.getId(), page, result -> showAudit(source, result, page));
        return 1;
    }

    private static int find(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Text.translatable("markettrade.history_searching"), false);
        AuditSearch.find(source.getServer(), StringArgumentType.getString(context, "text"), page,
                result -> showAudit(source, result, page));
        return 1;
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private static void showAudit(CommandSourceStack source, AuditFiles.Page result, int page) {
        if (result == null) {
            source.sendFailure(Text.translatable("markettrade.history_failed"));
            return;
        }
        if (result.entries().isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.history_none"), false);
            return;
        }
        source.sendSuccess(() -> Text.translatable("markettrade.history_header", page), false);
        for (AuditEntry entry : result.entries()) {
            String who = entry.actorName() != null ? entry.actorName() : entry.targetName() != null ? entry.targetName() : "-";
            String listing = entry.listing() == null ? "-" : String.valueOf(entry.listing());
            String item = entry.item() == null || entry.item().name() == null ? "-" : shorten(entry.item().name());
            source.sendSuccess(() -> Text.translatable("markettrade.history_line",
                    TIME.format(Instant.ofEpochMilli(entry.time())), entry.event(), who, listing, item), false);
        }
        if (result.more()) {
            source.sendSuccess(() -> Text.translatable("markettrade.history_more", page + 1), false);
        }
    }

    private static String shownWithTotal(long amount) {
        return Currencies.current().formatWithTotal(amount);
    }

    private static int adminPool(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MarketData market = MarketData.get(source.getServer());
        long pool = market.pool();
        long week = TopSellers.cutSince(market.sales().recent(), System.currentTimeMillis() - WEEK_MILLIS);
        source.sendSuccess(() -> Text.translatable("markettrade.admin_pool", shownWithTotal(pool)), false);
        source.sendSuccess(() -> Text.translatable("markettrade.pool_week", shownWithTotal(week)), false);
        return 1;
    }

    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private static int poolHistory(CommandSourceStack source, int page) {
        List<PoolEntry> all = MarketData.get(source.getServer()).ledgerNewestFirst();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.pool_history_none"), false);
            return 1;
        }
        int pages = Pagination.totalPages(all.size(), PAGE_SIZE);
        int index = Pagination.clampPage(page - 1, pages);
        source.sendSuccess(() -> Text.translatable("markettrade.pool_history_header", index + 1, pages), false);
        for (PoolEntry entry : all.subList(Pagination.startIndex(index, PAGE_SIZE),
                Pagination.endIndex(index, PAGE_SIZE, all.size()))) {
            String time = TIME.format(Instant.ofEpochMilli(entry.time()));
            String who = entry.actorName() == null || entry.actorName().isEmpty() ? "-" : entry.actorName();
            if (entry.kind() == PoolEntry.Kind.PAYOUT) {
                source.sendSuccess(() -> Text.translatable("markettrade.pool_history_payout", time, who,
                        shownWithTotal(entry.amount()), entry.targetName(), shownWithTotal(entry.poolAfter())), false);
            } else {
                String key = entry.note() == null || entry.note().isEmpty() ? "markettrade.pool_history_adjust"
                        : "markettrade.pool_history_adjust_note";
                source.sendSuccess(() -> Text.translatable(key, time, who, shownWithTotal(entry.poolAfter()),
                        shorten(entry.note() == null ? "" : entry.note())), false);
            }
        }
        return 1;
    }

    private static int poolTop(CommandSourceStack source, int page) {
        List<TopSellers.Row> all = MarketActions.topSellers(source.getServer());
        if (all.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("markettrade.pool_top_none"), false);
            return 1;
        }
        int pages = Pagination.totalPages(all.size(), PAGE_SIZE);
        int index = Pagination.clampPage(page - 1, pages);
        source.sendSuccess(() -> Text.translatable("markettrade.pool_top_header", index + 1, pages), false);
        int start = Pagination.startIndex(index, PAGE_SIZE);
        List<TopSellers.Row> shown = all.subList(start, Pagination.endIndex(index, PAGE_SIZE, all.size()));
        for (int i = 0; i < shown.size(); i++) {
            TopSellers.Row row = shown.get(i);
            int rank = start + i + 1;
            source.sendSuccess(() -> Text.translatable("markettrade.pool_top_line", rank, row.name(),
                    shownWithTotal(row.total())), false);
        }
        return 1;
    }

    private static int poolPay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = oneProfile(context);
        if (profile == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        MarketActions.PoolResult result = MarketActions.payFromPool(source.getServer(), actorOf(source), profile.getId(),
                profile.getName(), amount);
        switch (result.status()) {
            case OK -> {
                source.sendSuccess(() -> Text.translatable("markettrade.pool_pay_ok", shownWithTotal(amount),
                        profile.getName(), shownWithTotal(result.pool())), true);
                return 1;
            }
            case BAD_AMOUNT -> source.sendFailure(Text.translatable("markettrade.admin_invalid"));
            case NOT_ENOUGH -> source.sendFailure(Text.translatable("markettrade.pool_pay_not_enough",
                    shownWithTotal(result.pool())));
            case WALLET_FULL -> source.sendFailure(Text.translatable("markettrade.admin_overflow", profile.getName()));
        }
        return 0;
    }

    private static int poolSet(CommandContext<CommandSourceStack> context, String note) {
        CommandSourceStack source = context.getSource();
        long amount = LongArgumentType.getLong(context, "amount");
        MarketActions.PoolResult result = MarketActions.adjustPool(source.getServer(), actorOf(source), amount, note);
        if (result.status() == MarketActions.PoolStatus.OK) {
            source.sendSuccess(() -> Text.translatable("markettrade.pool_set_ok", shownWithTotal(amount)), true);
            return 1;
        }
        source.sendFailure(Text.translatable("markettrade.admin_invalid"));
        return 0;
    }

    public static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Text.translatable("markettrade.command_players_only"));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Text.translatable("markettrade.command_players_only"));
            return 0;
        }

        openMarket(player);
        return 1;
    }

    /** The better screen for a client that has the mod and passed the hello check, the chest screen for everyone else. */
    public static void openMarket(ServerPlayer player) {
        if (ClientTracker.hasValidHello(player.getUUID()) && player.connection.hasChannel(MarketPackets.OpenScreen.TYPE)) {
            PacketDistributor.sendToPlayer(player, new MarketPackets.OpenScreen(0));
        } else {
            openChest(player);
        }
    }

    /** Always the chest screen, so a player with the mod can still reach it. */
    public static int openChest(ServerPlayer player) {
        MarketChestMenu.open(player, 0);
        return 1;
    }
}
