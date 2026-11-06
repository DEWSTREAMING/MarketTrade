package com.markettrade.gui;

import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shows the listing and the price that was on the Browse page. Confirm buys at exactly that price. The buttons sit in the
 * bottom row, where no listing is, so a double click on a listing can never land on Confirm.
 */
final class ConfirmBuyView implements MarketView {
    private static final int ITEM_SLOT = 22;
    public static final int REPORT_SLOT = 49;

    private final BrowseState state;
    private final long listingId;
    private final long price;

    ConfirmBuyView(BrowseState state, long listingId, long price) {
        this.state = state;
        this.listingId = listingId;
        this.price = price;
    }

    @Override
    public void draw(MarketChestMenu menu) {
        Listing listing = MarketData.get(menu.player().server).listing(listingId);
        if (listing != null) {
            menu.put(ITEM_SLOT, ListingIcons.listing(listing, System.currentTimeMillis(),
                    Buttons.line("markettrade.id_lore", listing.id())));
        }
        menu.put(REPORT_SLOT, Buttons.of(Items.WRITABLE_BOOK, Buttons.name("markettrade.report_button"),
                Buttons.line("markettrade.report_button_lore"), Buttons.line("markettrade.report_button_cmd", listingId)));
        for (int slot = 45; slot <= 47; slot++) {
            menu.put(slot, Buttons.of(Items.LIME_STAINED_GLASS_PANE,
                    Buttons.name("markettrade.confirm_buy", ListingIcons.showWithTotal(price))));
        }
        for (int slot = 51; slot <= 53; slot++) {
            menu.put(slot, Buttons.of(Items.RED_STAINED_GLASS_PANE, Buttons.name("markettrade.go_back")));
        }
    }

    @Override
    public void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        if (slot >= 45 && slot <= 47) {
            MarketActions.BuyResult result = MarketActions.buy(player, listingId, price, System.currentTimeMillis());
            Buttons.say(player, com.markettrade.Replies.buy(result));
            menu.show(new BrowseView(state));
        } else if (slot == REPORT_SLOT) {
            MarketActions.ReportResult result = MarketActions.report(player, listingId, "", System.currentTimeMillis());
            Buttons.say(player, com.markettrade.Replies.report(result));
        } else if (slot >= 51 && slot <= 53) {
            menu.show(new BrowseView(state));
        }
    }
}
