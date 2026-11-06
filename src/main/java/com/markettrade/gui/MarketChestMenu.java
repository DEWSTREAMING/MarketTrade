package com.markettrade.gui;

import com.markettrade.Text;
import com.markettrade.market.StoredStack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The one 6 row chest screen. It holds a view and swaps it in the same window, so nothing reopens and the mouse stays put.
 * No item ever moves through it: {@code clicked} never calls {@code super.clicked}, and the views only ask the market to act.
 */
public class MarketChestMenu extends ChestMenu {
    public static final int LISTING_SLOT_COUNT = 45;
    public static final int PREV_SLOT = 45;
    public static final int SORT_SLOT = 46;
    public static final int SEARCH_SLOT = 47;
    public static final int MY_LISTINGS_SLOT = 48;
    public static final int PAGE_SLOT = 49;
    public static final int RETURNS_SLOT = 50;
    public static final int NEXT_SLOT = 51;
    public static final int WALLET_SLOT = 52;
    public static final int CLOSE_SLOT = 53;
    public static final int BACK_SLOT = 45;

    private static final int CHEST_SLOTS = 54;

    /**
     * What was drawn into a slot: a listing id and its price, a Returns index and its stack, or a currency item id. Clicks use
     * this and never the current position, because the market may have changed since the slot was drawn.
     */
    record Drawn(long id, long price, int index, StoredStack stack, String text) {
        static Drawn listing(long id, long price) {
            return new Drawn(id, price, -1, null, null);
        }

        static Drawn returned(int index, StoredStack stack) {
            return new Drawn(-1, 0, index, stack, null);
        }

        static Drawn currency(String itemId) {
            return new Drawn(-1, 0, -1, null, itemId);
        }
    }

    private final ServerPlayer player;
    private final BrowseState state;
    private final Drawn[] drawn = new Drawn[CHEST_SLOTS];
    private MarketView view;
    private boolean locked;
    private int lastActionSlot = -1;
    private long lastActionTick = Long.MIN_VALUE;

    private MarketChestMenu(int containerId, ServerPlayer player, BrowseState state) {
        super(MenuType.GENERIC_9x6, containerId, player.getInventory(), new SimpleContainer(CHEST_SLOTS), 6);
        this.player = player;
        this.state = state;
    }

    /** Opens the Browse page on {@code page}. */
    public static void open(ServerPlayer player, int page) {
        BrowseState state = new BrowseState();
        state.page = Math.max(0, page);
        open(player, state, new BrowseView(state));
    }

    static void open(ServerPlayer player, BrowseState state, MarketView view) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            MarketChestMenu menu = new MarketChestMenu(containerId, player, state);
            menu.setView(view);
            return menu;
        }, Text.translatable("markettrade.market_title")));
    }

    public int getCurrentPage() {
        return state.page;
    }

    public int getTotalPages() {
        return state.totalPages;
    }

    ServerPlayer player() {
        return player;
    }

    BrowseState state() {
        return state;
    }

    /** Draws a view without sending it. Used while the menu is being built. */
    private void setView(MarketView next) {
        this.view = next;
        redraw();
    }

    /** Swaps the view in this same window and sends it. */
    void show(MarketView next) {
        this.view = next;
        redraw();
        sendAllDataToRemote();
    }

    private void redraw() {
        Container container = getContainer();
        for (int i = 0; i < CHEST_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
            drawn[i] = null;
        }
        view.draw(this);
    }

    void put(int slot, ItemStack stack) {
        getContainer().setItem(slot, stack);
    }

    void put(int slot, ItemStack stack, Drawn what) {
        getContainer().setItem(slot, stack);
        drawn[slot] = what;
    }

    Drawn drawn(int slot) {
        return slot >= 0 && slot < CHEST_SLOTS ? drawn[slot] : null;
    }

    /** Ignores every click from now on. Used while a switch to another menu waits for the next tick. */
    void lock() {
        this.locked = true;
    }

    /**
     * True if this slot was just acted on. A double click sends two clicks before the player sees the redraw, and the second
     * would land on whatever slid into that slot. Used where an action removes an entry from the list.
     */
    boolean repeatedClick(int slot) {
        long tick = player.server.getTickCount();
        boolean repeat = slot == lastActionSlot && tick - lastActionTick >= 0 && tick - lastActionTick < 4;
        lastActionSlot = slot;
        lastActionTick = tick;
        return repeat;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        // Never call super.clicked(...): that is what moves items. Only these two types do anything, so a
        // double click or a drag can't fire an action twice.
        boolean acts = clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE;
        if (acts && !locked && clicker == player) {
            view.click(this, player, slotId, button, clickType);
        }
        if (player.containerMenu == this) {
            sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
