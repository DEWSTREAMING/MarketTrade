package com.markettrade.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.markettrade.client.theme.Theme;
import com.markettrade.client.theme.Themes;
import com.markettrade.market.MarketViews;
import com.markettrade.market.SummaryRow;
import com.markettrade.net.MarketPackets;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/** What the server last sent to the market screen, and the calls that ask for more. Client only. */
public final class ClientData {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static volatile Theme theme = Themes.MARKET;

    public static MarketViews.Status status;
    public static final List<SummaryRow> SUMMARY = new ArrayList<>();
    public static boolean summaryReady;
    public static MarketViews.ItemPage item;
    public static MarketViews.MyListings mine;
    public static MarketViews.Returns returns;
    public static MarketViews.WalletView wallet;
    public static MarketViews.SellPreview preview;
    public static boolean replyOk;
    public static List<Component> replyLines = List.of();
    public static long replyAt;

    private static int generation;
    private static int summaryGen;
    private static int itemGen;
    private static int mineGen;
    private static int returnsGen;

    private ClientData() {
    }

    // Theme file

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("markettrade-client.json");
    }

    public static void loadTheme() {
        theme = ClientConfig.read(configFile(), LOGGER::warn);
    }

    public static void setTheme(Theme chosen) {
        theme = chosen;
        if (!ClientConfig.write(configFile(), chosen)) {
            LOGGER.warn("Could not save {}", configFile());
        }
    }

    // Requests

    public static void send(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(payload.type())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public static void requestSummary() {
        summaryGen = ++generation;
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.SUMMARY.ordinal(), 0, 0, "", summaryGen));
    }

    public static void requestItem(String itemId, int page, int pageSize) {
        itemGen = ++generation;
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.ITEM.ordinal(), page, pageSize, itemId, itemGen));
    }

    public static void requestMine(int page, int pageSize) {
        mineGen = ++generation;
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.MINE.ordinal(), page, pageSize, "", mineGen));
    }

    public static void requestReturns(int page, int pageSize) {
        returnsGen = ++generation;
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.RETURNS.ordinal(), page, pageSize, "", returnsGen));
    }

    public static void requestWallet() {
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.WALLET.ordinal(), 0, 0, "", ++generation));
    }

    public static void requestStatus() {
        send(new MarketPackets.ViewRequest(MarketPackets.ViewKind.STATUS.ordinal(), 0, 0, "", ++generation));
    }

    public static void reset() {
        status = null;
        SUMMARY.clear();
        summaryReady = false;
        item = null;
        mine = null;
        returns = null;
        wallet = null;
        preview = null;
        replyLines = List.of();
    }

    // Answers

    public static void accept(CustomPacketPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload instanceof MarketPackets.OpenScreen) {
            mc.setScreen(new MarketScreen());
            return;
        }
        if (payload instanceof MarketPackets.StatusPayload p) {
            status = p.status();
        } else if (payload instanceof MarketPackets.SummaryChunk p) {
            if (p.generation() != summaryGen) {
                return;
            }
            if (p.first()) {
                SUMMARY.clear();
            }
            SUMMARY.addAll(p.rows());
            summaryReady = SUMMARY.size() >= p.total();
        } else if (payload instanceof MarketPackets.ItemPagePayload p) {
            if (p.generation() != itemGen) {
                return;
            }
            item = p.page();
        } else if (payload instanceof MarketPackets.MyListingsPayload p) {
            if (p.generation() != mineGen) {
                return;
            }
            mine = p.data();
        } else if (payload instanceof MarketPackets.ReturnsPayload p) {
            if (p.generation() != returnsGen) {
                return;
            }
            returns = p.data();
        } else if (payload instanceof MarketPackets.WalletPayload p) {
            wallet = p.data();
        } else if (payload instanceof MarketPackets.SellPreviewPayload p) {
            preview = p.data();
        } else if (payload instanceof MarketPackets.ReplyPayload p) {
            replyOk = p.ok();
            replyLines = p.lines();
            replyAt = System.currentTimeMillis();
            if (mc.screen instanceof MarketScreen screen) {
                screen.afterReply();
            }
            return;
        }
        if (mc.screen instanceof MarketScreen screen) {
            screen.dataChanged(payload);
        }
    }
}
