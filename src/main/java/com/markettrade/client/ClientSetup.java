package com.markettrade.client;

import org.lwjgl.glfw.GLFW;

import com.markettrade.net.HelloPayload;
import com.markettrade.net.MarketPackets;
import com.markettrade.net.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientSetup {
    // K was free in the Holey Digsite pack. Key mappings live in options.txt, not a registry, so this stays client only.
    public static final KeyMapping OPEN_MARKET = new KeyMapping("key.markettrade.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.markettrade");

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        ModNetworking.clientSink = ClientData::accept;
        ClientData.loadTheme();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_MARKET));
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_MARKET.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener connection = mc.getConnection();
            if (mc.player == null || connection == null || mc.screen != null) {
                continue;
            }
            if (connection.hasChannel(MarketPackets.OpenRequest.TYPE)) {
                PacketDistributor.sendToServer(new MarketPackets.OpenRequest());
            } else if (connection.hasChannel(HelloPayload.TYPE)) {
                // An older Market Trade on the server: the command still works there.
                connection.sendCommand("market");
            } else {
                mc.player.displayClientMessage(Component.translatable("markettrade.key_no_market"), true);
            }
        }
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientData.reset();
        Categories.reset();
        // Sending on a channel the server doesn't have throws, and players with the mod join other servers too.
        if (event.getPlayer().connection.hasChannel(HelloPayload.TYPE)) {
            PacketDistributor.sendToServer(new HelloPayload(ModNetworking.PROTOCOL_VERSION));
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientData.reset();
        Categories.reset();
    }
}
