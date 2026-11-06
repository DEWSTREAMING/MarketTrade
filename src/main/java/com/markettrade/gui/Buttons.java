package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.Text;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Small helpers for drawing buttons. Vanilla items only, so every client can show them. */
final class Buttons {
    private Buttons() {
    }

    /** Lore and names are italic by default, which looks wrong on a button. */
    static MutableComponent plain(MutableComponent text, ChatFormatting color) {
        return text.withStyle(style -> style.withItalic(false).withColor(color));
    }

    static ItemStack of(Item item, Component name, Component... lore) {
        return of(item, 1, name, lore);
    }

    static ItemStack of(Item item, int count, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(count, 64)));
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        }
        return stack;
    }

    static MutableComponent name(String key, Object... args) {
        return plain(Text.translatable(key, args), ChatFormatting.WHITE);
    }

    static MutableComponent line(String key, Object... args) {
        return plain(Text.translatable(key, args), ChatFormatting.GRAY);
    }

    /** Adds lines to a copy's lore, keeping its own lines. A stack that is already full of lore gets nothing more. */
    static void addLore(ItemStack stack, List<Component> extra) {
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component line : extra) {
            if (lore.lines().size() >= ItemLore.MAX_LINES) {
                break;
            }
            lore = lore.withLineAdded(line);
        }
        stack.set(DataComponents.LORE, lore);
    }

    static void say(ServerPlayer player, Replies.Reply reply) {
        for (Component line : reply.lines()) {
            player.sendSystemMessage(reply.ok() ? line : line.copy().withStyle(ChatFormatting.RED));
        }
    }
}
