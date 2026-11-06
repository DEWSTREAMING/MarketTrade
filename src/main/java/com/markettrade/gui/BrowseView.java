package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.market.AmountParser;
import com.markettrade.market.Listing;
import com.markettrade.market.ListingSort;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketData;
import com.markettrade.market.Pagination;
import com.markettrade.market.WalletActions;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The main page: listings, sorting, searching and the way to every other page. */
final class BrowseView implements MarketView {
    private static final int FIRST_INVENTORY_SLOT = 54;

    private final BrowseState state;

    BrowseView(BrowseState state) {
        this.state = state;
    }

    private List<Listing> visible(MarketData market) {
        List<Listing> matching = market.listingsNewestFirst().stream()
                .filter(l -> ListingSort.matches(ListingIcons.shownName(l.item()), l.item().itemId(), state.search))
                .toList();
        return ListingSort.sorted(matching, state.sort, Listing::id, Listing::price, Listing::expiresAt,
                l -> ListingIcons.shownName(l.item()));
    }

    @Override
    public void draw(MarketChestMenu menu) {
        ServerPlayer player = menu.player();
        MarketData market = MarketData.get(player.server);
        long now = System.currentTimeMillis();

        List<Listing> shown = visible(market);
        int total = Pagination.totalPages(shown.size(), MarketChestMenu.LISTING_SLOT_COUNT);
        state.totalPages = total;
        state.page = Pagination.clampPage(state.page, total);

        int start = Pagination.startIndex(state.page, MarketChestMenu.LISTING_SLOT_COUNT);
        int end = Pagination.endIndex(state.page, MarketChestMenu.LISTING_SLOT_COUNT, shown.size());
        for (int i = start; i < end; i++) {
            Listing listing = shown.get(i);
            ItemStack icon = ListingIcons.listing(listing, now,
                    Buttons.plain(Text.translatable("markettrade.click_to_buy"), ChatFormatting.YELLOW));
            menu.put(i - start, icon, MarketChestMenu.Drawn.listing(listing.id(), listing.price()));
        }

        if (state.page > 0) {
            menu.put(MarketChestMenu.PREV_SLOT, Buttons.of(Items.ARROW, Buttons.name("markettrade.previous_page")));
        }
        menu.put(MarketChestMenu.SORT_SLOT, Buttons.of(Items.HOPPER,
                Buttons.name("markettrade.sort_button", Text.translatable("markettrade.sort_" + state.sort.key())),
                Buttons.line("markettrade.sort_hint")));
        Component current = state.search.isEmpty() ? Buttons.line("markettrade.search_none")
                : Buttons.line("markettrade.search_current", state.search);
        menu.put(MarketChestMenu.SEARCH_SLOT, Buttons.of(Items.SPYGLASS, Buttons.name("markettrade.search_button"), current,
                Buttons.line("markettrade.search_hint_left"), Buttons.line("markettrade.search_hint_right")));
        menu.put(MarketChestMenu.MY_LISTINGS_SLOT, Buttons.of(Items.CHEST, Buttons.name("markettrade.my_listings_button")));
        menu.put(MarketChestMenu.PAGE_SLOT, Buttons.of(Items.PAPER, state.page + 1,
                Buttons.name("markettrade.page_indicator", state.page + 1, total)));
        int waiting = MarketActions.returnsCount(player.server, player.getUUID());
        menu.put(MarketChestMenu.RETURNS_SLOT, Buttons.of(Items.ENDER_CHEST, Math.max(1, waiting),
                Buttons.name("markettrade.returns_button"), Buttons.line("markettrade.returns_waiting_lore", waiting)));
        if (state.page < total - 1) {
            menu.put(MarketChestMenu.NEXT_SLOT, Buttons.of(Items.ARROW, Buttons.name("markettrade.next_page")));
        }
        long balance = WalletActions.balance(player.server, player.getUUID());
        menu.put(MarketChestMenu.WALLET_SLOT, Buttons.of(Items.GOLD_INGOT, Buttons.name("markettrade.wallet_button"),
                Buttons.line("markettrade.balance", ListingIcons.showWithTotal(balance))));
        menu.put(MarketChestMenu.CLOSE_SLOT, Buttons.of(Items.BARRIER, Buttons.name("markettrade.close")));
    }

    @Override
    public void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        if (slot >= 0 && slot < MarketChestMenu.LISTING_SLOT_COUNT) {
            clickListing(menu, player, menu.drawn(slot));
            return;
        }
        switch (slot) {
            case MarketChestMenu.PREV_SLOT -> {
                if (state.page > 0) {
                    state.page--;
                    menu.show(this);
                }
            }
            case MarketChestMenu.NEXT_SLOT -> {
                if (state.page < state.totalPages - 1) {
                    state.page++;
                    menu.show(this);
                }
            }
            case MarketChestMenu.SORT_SLOT -> {
                state.sort = state.sort.next();
                state.page = 0;
                menu.show(this);
            }
            case MarketChestMenu.SEARCH_SLOT -> {
                if (button == 1) {
                    if (!state.search.isEmpty()) {
                        state.search = "";
                        state.page = 0;
                        menu.show(this);
                    }
                } else {
                    askSearch(menu, player);
                }
            }
            case MarketChestMenu.MY_LISTINGS_SLOT -> menu.show(new MyListingsView(state, 0));
            case MarketChestMenu.RETURNS_SLOT -> menu.show(new ReturnsView(state, 0));
            case MarketChestMenu.WALLET_SLOT -> menu.show(new WalletView(state));
            case MarketChestMenu.CLOSE_SLOT -> player.closeContainer();
            default -> {
                if (type == ClickType.PICKUP && button == 0 && slot >= FIRST_INVENTORY_SLOT && slot < menu.slots.size()) {
                    startSelling(menu, player, menu.getSlot(slot));
                }
            }
        }
    }

    private void clickListing(MarketChestMenu menu, ServerPlayer player, MarketChestMenu.Drawn drawn) {
        if (drawn == null) {
            return;
        }
        MarketData market = MarketData.get(player.server);
        Listing listing = market.listing(drawn.id());
        if (market.isBanned(player.getUUID())) {
            tell(player, Text.translatable("markettrade.banned"));
        } else if (listing == null) {
            tell(player, Text.translatable("markettrade.buy_not_found"));
            menu.show(this);
        } else if (listing.seller().equals(player.getUUID())) {
            tell(player, Text.translatable("markettrade.buy_own"));
        } else if (!listing.item().isAvailable()) {
            tell(player, Text.translatable("markettrade.buy_unavailable"));
        } else if (listing.price() != drawn.price()) {
            tell(player, Text.translatable("markettrade.buy_price_changed", ListingIcons.show(listing.price())));
            menu.show(this);
        } else {
            menu.show(new ConfirmBuyView(state, drawn.id(), drawn.price()));
        }
    }

    private static void tell(ServerPlayer player, Component line) {
        player.sendSystemMessage(line.copy().withStyle(ChatFormatting.RED));
    }

    private void askSearch(MarketChestMenu menu, ServerPlayer player) {
        menu.lock();
        TextEntryMenu.ask(player, menu, Text.translatable("markettrade.enter_search"), state.search,
                Text.translatable("markettrade.text_search_hint"), new TextEntryMenu.Handler() {
                    @Override
                    public Runnable submit(ServerPlayer p, String text) {
                        state.search = text.trim();
                        state.page = 0;
                        return () -> MarketChestMenu.open(p, state, new BrowseView(state));
                    }

                    @Override
                    public Runnable cancel(ServerPlayer p) {
                        return () -> MarketChestMenu.open(p, state, new BrowseView(state));
                    }
                });
    }

    /** A left click on a stack in the player's own inventory. The item stays where it is until Confirm Sell. */
    private void startSelling(MarketChestMenu menu, ServerPlayer player, Slot clicked) {
        int inventorySlot = clicked.getContainerSlot();
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty()) {
            return;
        }
        if (MarketData.get(player.server).isBanned(player.getUUID())) {
            tell(player, Text.translatable("markettrade.banned"));
            return;
        }
        ItemStack expected = stack.copy();
        menu.lock();
        TextEntryMenu.ask(player, menu, Text.translatable("markettrade.enter_price"), "",
                Text.translatable("markettrade.text_price_hint"), new TextEntryMenu.Handler() {
                    @Override
                    public Runnable submit(ServerPlayer p, String text) {
                        AmountParser.Result result = AmountParser.parse(text, Currencies.current());
                        if (!result.ok()) {
                            tell(p, Replies.amountProblem(result.status()));
                            return null;
                        }
                        if (result.amount() < 1) {
                            tell(p, Text.translatable("markettrade.sell_bad_price"));
                            return null;
                        }
                        return () -> MarketChestMenu.open(p, state,
                                new ConfirmSellView(state, inventorySlot, expected, result.amount()));
                    }

                    @Override
                    public Runnable cancel(ServerPlayer p) {
                        return () -> MarketChestMenu.open(p, state, new BrowseView(state));
                    }
                });
    }
}
