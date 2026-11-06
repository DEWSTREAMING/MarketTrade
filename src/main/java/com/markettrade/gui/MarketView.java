package com.markettrade.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;

/** One page of the chest screen. It draws into the menu and handles its own clicks, and holds no rules. */
interface MarketView {
    void draw(MarketChestMenu menu);

    /** Called for PICKUP and QUICK_MOVE only. {@code slot} can be any menu slot, or -999 for outside the window. */
    void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type);
}
