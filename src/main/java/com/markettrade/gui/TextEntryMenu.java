package com.markettrade.gui;

import java.util.List;

import com.markettrade.Text;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * A text box for clients without the mod: the rename box of a vanilla anvil, which is the only text field they have.
 * Slot 0 is a paper named with the text, slot 1 is Cancel and slot 2 is Confirm. Nothing can be taken out of it.
 *
 * <p>The access is {@code ContainerLevelAccess.NULL}, so {@code removed()} has no block to clear the input slots into and
 * never hands the paper to the player. {@code createResult} only sets the Confirm item, so the level cost stays at 0 and no
 * anvil event fires.
 */
public class TextEntryMenu extends AnvilMenu {
    private static final int CANCEL_SLOT = 1;
    private static final int CONFIRM_SLOT = 2;

    /** What happens with the text. Each returns what to run on the next tick, or null to stay on the text screen. */
    interface Handler {
        Runnable submit(ServerPlayer player, String text);

        Runnable cancel(ServerPlayer player);
    }

    private final ServerPlayer player;
    private final Handler handler;
    private final Component hint;
    private String typed;
    private boolean finished;

    private TextEntryMenu(int containerId, ServerPlayer player, String initial, Component hint, Handler handler) {
        super(containerId, player.getInventory(), ContainerLevelAccess.NULL);
        this.player = player;
        this.handler = handler;
        this.hint = hint;
        this.typed = initial;
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(initial));
        this.inputSlots.setItem(0, paper);
        this.inputSlots.setItem(CANCEL_SLOT, cancelButton());
        createResult();
    }

    /** Opens the text screen on the next tick. {@code from} must still be the player's open menu by then. */
    static void ask(ServerPlayer player, AbstractContainerMenu from, Component title, String initial, Component hint,
            Handler handler) {
        Later.run(player, from, () -> player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new TextEntryMenu(containerId, player, initial, hint, handler), title)));
    }

    /** The text as the server has it. */
    public String typed() {
        return typed;
    }

    private static ItemStack cancelButton() {
        ItemStack stack = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Buttons.name("markettrade.text_cancel"));
        return stack;
    }

    private ItemStack confirmButton() {
        ItemStack stack = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Buttons.name("markettrade.text_confirm"));
        if (hint != null) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(Buttons.plain(hint.copy(), ChatFormatting.GRAY))));
        }
        return stack;
    }

    /** Sets the Confirm item and nothing else, so the cost stays 0 and the vanilla anvil logic never runs. */
    @Override
    public void createResult() {
        this.resultSlots.setItem(0, confirmButton());
    }

    /**
     * Keeps the text, then lets the anvil do its own bookkeeping. The anvil rewrites the result, which createResult resets. On a
     * client this is a plain anvil, and typing empties the result slot there because paper and glass make no recipe. The server's
     * slot hasn't changed, so nothing would be sent back without the resend, and Confirm would vanish.
     */
    @Override
    public boolean setItemName(String itemName) {
        if (itemName.length() <= MAX_NAME_LENGTH) {
            this.typed = itemName;
        }
        boolean changed = super.setItemName(itemName);
        if (player.containerMenu == this) {
            sendAllDataToRemote();
        }
        return changed;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        // Never call super.clicked(...): that is what moves items.
        boolean acts = clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE;
        if (acts && !finished && clicker == player) {
            if (slotId == CANCEL_SLOT) {
                finished = true;
                Later.run(player, this, handler.cancel(player));
            } else if (slotId == CONFIRM_SLOT) {
                Runnable next = handler.submit(player, typed == null ? "" : typed);
                if (next != null) {
                    finished = true;
                    Later.run(player, this, next);
                }
            }
        }
        if (player.containerMenu == this) {
            sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
