package com.markettrade.gui;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Opening a menu from inside a click is not safe: the click handler goes on to apply the client's changed slots to
 * whichever menu is open by then. So anything that opens a different menu waits until the click is finished.
 * {@code server.tell} queues it for after the current tick. {@code server.execute} would run it at once.
 */
final class Later {
    private Later() {
    }

    /** Runs the action later, but only if the player is still there and still has {@code from} open. */
    static void run(ServerPlayer player, AbstractContainerMenu from, Runnable action) {
        MinecraftServer server = player.server;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (player.hasDisconnected() || player.containerMenu != from) {
                return;
            }
            action.run();
        }));
    }
}
