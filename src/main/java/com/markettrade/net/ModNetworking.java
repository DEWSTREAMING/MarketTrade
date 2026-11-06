package com.markettrade.net;

import java.util.function.Consumer;

import com.markettrade.MarketTrade;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    // Channel version string is fixed at "1" forever.
    // In NeoForge, an optional channel means "fine if the other side lacks it". If both sides have
    // the channel and the version strings differ, negotiation fails and the player is disconnected.
    // The real version negotiation happens via the int protocolVersion inside HelloPayload.
    public static final String CHANNEL_VERSION = "1";

    /** 2: the client screen with its own requests. A client that says 1 gets chest screens. */
    public static final int PROTOCOL_VERSION = 2;

    /** Set on the client only. Server code never touches client classes through it. */
    public static volatile Consumer<CustomPacketPayload> clientSink = null;

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MarketTrade.MODID)
                .versioned(CHANNEL_VERSION)
                .optional();

        registrar.playToServer(HelloPayload.TYPE, HelloPayload.STREAM_CODEC, HelloPayload::handle);

        registrar.playToServer(MarketPackets.ViewRequest.TYPE, MarketPackets.ViewRequest.CODEC,
                server((p, sp) -> MarketRequests.view(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.BuyRequest.TYPE, MarketPackets.BuyRequest.CODEC,
                server((p, sp) -> MarketRequests.buy(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.SellRequest.TYPE, MarketPackets.SellRequest.CODEC,
                server((p, sp) -> MarketRequests.sell(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.CancelRequest.TYPE, MarketPackets.CancelRequest.CODEC,
                server((p, sp) -> MarketRequests.cancel(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.ClaimRequest.TYPE, MarketPackets.ClaimRequest.CODEC,
                server((p, sp) -> MarketRequests.claim(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.WalletRequest.TYPE, MarketPackets.WalletRequest.CODEC,
                server((p, sp) -> MarketRequests.wallet(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.OpenRequest.TYPE, MarketPackets.OpenRequest.CODEC,
                server((p, sp) -> MarketRequests.open(sp, System.currentTimeMillis())));
        registrar.playToServer(MarketPackets.ReportRequest.TYPE, MarketPackets.ReportRequest.CODEC,
                server((p, sp) -> MarketRequests.report(sp, p, MarketRequests.connection(sp), System.currentTimeMillis())));

        registrar.playToClient(MarketPackets.OpenScreen.TYPE, MarketPackets.OpenScreen.CODEC, client());
        registrar.playToClient(MarketPackets.StatusPayload.TYPE, MarketPackets.StatusPayload.CODEC, client());
        registrar.playToClient(MarketPackets.SummaryChunk.TYPE, MarketPackets.SummaryChunk.CODEC, client());
        registrar.playToClient(MarketPackets.ItemPagePayload.TYPE, MarketPackets.ItemPagePayload.CODEC, client());
        registrar.playToClient(MarketPackets.MyListingsPayload.TYPE, MarketPackets.MyListingsPayload.CODEC, client());
        registrar.playToClient(MarketPackets.ReturnsPayload.TYPE, MarketPackets.ReturnsPayload.CODEC, client());
        registrar.playToClient(MarketPackets.WalletPayload.TYPE, MarketPackets.WalletPayload.CODEC, client());
        registrar.playToClient(MarketPackets.SellPreviewPayload.TYPE, MarketPackets.SellPreviewPayload.CODEC, client());
        registrar.playToClient(MarketPackets.ReplyPayload.TYPE, MarketPackets.ReplyPayload.CODEC, client());
    }

    private interface ServerWork<T> {
        void run(T payload, ServerPlayer player);
    }

    private static <T extends CustomPacketPayload> net.neoforged.neoforge.network.handling.IPayloadHandler<T> server(
            ServerWork<T> work) {
        return (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                work.run(payload, player);
            }
        });
    }

    private static <T extends CustomPacketPayload> net.neoforged.neoforge.network.handling.IPayloadHandler<T> client() {
        return (payload, context) -> deliver(payload, context);
    }

    private static void deliver(CustomPacketPayload payload, IPayloadContext context) {
        Consumer<CustomPacketPayload> sink = clientSink;
        if (sink != null) {
            context.enqueueWork(() -> sink.accept(payload));
        }
    }
}
