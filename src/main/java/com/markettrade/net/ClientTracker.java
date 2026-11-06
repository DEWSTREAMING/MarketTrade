package com.markettrade.net;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ClientTracker {
    private static final Set<UUID> verifiedPlayers = ConcurrentHashMap.newKeySet();

    private ClientTracker() {
    }

    public static void recordHello(UUID playerUuid) {
        verifiedPlayers.add(playerUuid);
    }

    public static boolean hasValidHello(UUID playerUuid) {
        return verifiedPlayers.contains(playerUuid);
    }

    public static void onLogout(UUID playerUuid) {
        verifiedPlayers.remove(playerUuid);
    }

    public static void clear() {
        verifiedPlayers.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        onLogout(event.getEntity().getUUID());
    }
}
