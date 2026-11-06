package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.market.Cut;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfigs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shows the stack the player picked, the price, the cut and what they get. The action checks again that the slot still holds
 * exactly this stack.
 */
final class ConfirmSellView implements MarketView {
    private static final int ITEM_SLOT = 22;

    private final BrowseState state;
    private final int inventorySlot;
    private final ItemStack expected;
    private final long price;

    ConfirmSellView(BrowseState state, int inventorySlot, ItemStack expected, long price) {
        this.state = state;
        this.inventorySlot = inventorySlot;
        this.expected = expected.copy();
        this.price = price;
    }

    @Override
    public void draw(MarketChestMenu menu) {
        long cut = Cut.of(price, MarketConfigs.current().cutPercent());
        ItemStack icon = expected.copy();
        Buttons.addLore(icon, List.of(
                Buttons.plain(com.markettrade.Text.translatable("markettrade.price_lore", ListingIcons.showWithTotal(price)),
                        net.minecraft.ChatFormatting.GOLD),
                Buttons.line("markettrade.sell_cut_lore", ListingIcons.show(cut)),
                Buttons.line("markettrade.sell_receive_lore", ListingIcons.show(price - cut))));
        menu.put(ITEM_SLOT, icon);
        for (int slot = 45; slot <= 47; slot++) {
            menu.put(slot, Buttons.of(Items.LIME_STAINED_GLASS_PANE,
                    Buttons.name("markettrade.confirm_sell", ListingIcons.showWithTotal(price))));
        }
        for (int slot = 51; slot <= 53; slot++) {
            menu.put(slot, Buttons.of(Items.RED_STAINED_GLASS_PANE, Buttons.name("markettrade.go_back")));
        }
    }

    @Override
    public void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        if (slot >= 45 && slot <= 47) {
            MarketActions.SellResult result = MarketActions.sell(player, inventorySlot, expected, price,
                    System.currentTimeMillis());
            Buttons.say(player, Replies.sell(result, expected, price));
            menu.show(new BrowseView(state));
        } else if (slot >= 51 && slot <= 53) {
            menu.show(new BrowseView(state));
        }
    }
}
