package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;
import com.markettrade.market.Pagination;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/** The player's own listings. A click cancels one and the item goes to Returns. */
final class MyListingsView implements MarketView {
    private static final int PREV = 46;
    private static final int PAGE = 49;
    private static final int NEXT = 52;

    private final BrowseState state;
    private int page;
    private int totalPages = 1;

    MyListingsView(BrowseState state, int page) {
        this.state = state;
        this.page = page;
    }

    @Override
    public void draw(MarketChestMenu menu) {
        ServerPlayer player = menu.player();
        List<Listing> own = MarketData.get(player.server).listingsOf(player.getUUID());
        totalPages = Pagination.totalPages(own.size(), MarketChestMenu.LISTING_SLOT_COUNT);
        page = Pagination.clampPage(page, totalPages);
        long now = System.currentTimeMillis();

        int start = Pagination.startIndex(page, MarketChestMenu.LISTING_SLOT_COUNT);
        int end = Pagination.endIndex(page, MarketChestMenu.LISTING_SLOT_COUNT, own.size());
        for (int i = start; i < end; i++) {
            Listing listing = own.get(i);
            menu.put(i - start, ListingIcons.listing(listing, now,
                    Buttons.plain(Text.translatable("markettrade.click_to_cancel"), ChatFormatting.YELLOW)),
                    MarketChestMenu.Drawn.listing(listing.id(), listing.price()));
        }
        if (own.isEmpty()) {
            menu.put(22, Buttons.of(Items.PAPER, Buttons.name("markettrade.mine_none")));
        }

        menu.put(MarketChestMenu.BACK_SLOT, Buttons.of(Items.ARROW, Buttons.name("markettrade.back")));
        if (page > 0) {
            menu.put(PREV, Buttons.of(Items.ARROW, Buttons.name("markettrade.previous_page")));
        }
        menu.put(PAGE, Buttons.of(Items.PAPER, page + 1, Buttons.name("markettrade.page_indicator", page + 1, totalPages)));
        if (page < totalPages - 1) {
            menu.put(NEXT, Buttons.of(Items.ARROW, Buttons.name("markettrade.next_page")));
        }
    }

    @Override
    public void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        if (slot >= 0 && slot < MarketChestMenu.LISTING_SLOT_COUNT) {
            MarketChestMenu.Drawn drawn = menu.drawn(slot);
            if (drawn != null && !menu.repeatedClick(slot)) {
                Buttons.say(player, Replies.cancel(MarketActions.cancel(player, drawn.id()), drawn.id()));
                menu.show(this);
            }
        } else if (slot == MarketChestMenu.BACK_SLOT) {
            menu.show(new BrowseView(state));
        } else if (slot == PREV && page > 0) {
            page--;
            menu.show(this);
        } else if (slot == NEXT && page < totalPages - 1) {
            page++;
            menu.show(this);
        }
    }
}
