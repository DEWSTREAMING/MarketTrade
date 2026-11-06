package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;
import com.markettrade.market.Pagination;
import com.markettrade.market.StoredStack;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/** Items waiting for the player. A click claims one stack, Claim All takes what fits. Nothing here ever deletes a stack. */
final class ReturnsView implements MarketView {
    private static final int PREV = 46;
    private static final int CLAIM_ALL = 48;
    private static final int PAGE = 49;
    private static final int NEXT = 52;

    private final BrowseState state;
    private int page;
    private int totalPages = 1;

    ReturnsView(BrowseState state, int page) {
        this.state = state;
        this.page = page;
    }

    @Override
    public void draw(MarketChestMenu menu) {
        ServerPlayer player = menu.player();
        List<StoredStack> entries = MarketData.get(player.server).returnsOf(player.getUUID());
        totalPages = Pagination.totalPages(entries.size(), MarketChestMenu.LISTING_SLOT_COUNT);
        page = Pagination.clampPage(page, totalPages);

        int start = Pagination.startIndex(page, MarketChestMenu.LISTING_SLOT_COUNT);
        int end = Pagination.endIndex(page, MarketChestMenu.LISTING_SLOT_COUNT, entries.size());
        for (int i = start; i < end; i++) {
            StoredStack entry = entries.get(i);
            menu.put(i - start, ListingIcons.returned(entry,
                    Buttons.plain(Text.translatable("markettrade.click_to_claim"), ChatFormatting.YELLOW)),
                    MarketChestMenu.Drawn.returned(i, entry));
        }
        if (entries.isEmpty()) {
            menu.put(22, Buttons.of(Items.PAPER, Buttons.name("markettrade.returns_none")));
        }

        menu.put(MarketChestMenu.BACK_SLOT, Buttons.of(Items.ARROW, Buttons.name("markettrade.back")));
        if (page > 0) {
            menu.put(PREV, Buttons.of(Items.ARROW, Buttons.name("markettrade.previous_page")));
        }
        if (!entries.isEmpty()) {
            menu.put(CLAIM_ALL, Buttons.of(Items.HOPPER, Buttons.name("markettrade.claim_all")));
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
                MarketActions.ClaimOneResult result = MarketActions.claimReturn(player, drawn.index(), drawn.stack());
                Buttons.say(player, Replies.claimOne(result));
                menu.show(this);
            }
        } else if (slot == MarketChestMenu.BACK_SLOT) {
            menu.show(new BrowseView(state));
        } else if (slot == CLAIM_ALL) {
            if (MarketActions.returnsCount(player.server, player.getUUID()) > 0) {
                Buttons.say(player, Replies.claimAll(MarketActions.claimReturns(player)));
            }
            menu.show(this);
        } else if (slot == PREV && page > 0) {
            page--;
            menu.show(this);
        } else if (slot == NEXT && page < totalPages - 1) {
            page++;
            menu.show(this);
        }
    }
}
