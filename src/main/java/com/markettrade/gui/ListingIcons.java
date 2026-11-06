package com.markettrade.gui;

import java.util.ArrayList;
import java.util.List;

import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.market.Listing;
import com.markettrade.market.StoredStack;
import com.markettrade.market.TimeLeft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** How listings and Returns entries look in a slot. Always a copy, the real stack stays in the market. */
final class ListingIcons {
    private ListingIcons() {
    }

    static String show(long amount) {
        return Currencies.current().format(amount);
    }

    /** For prices and balances, where the typed amount is the plain total. */
    static String showWithTotal(long amount) {
        return Currencies.current().formatWithTotal(amount);
    }

    /** The name a player sees, or the item id for a stack that can't load. */
    static String shownName(StoredStack stored) {
        return stored.isAvailable() ? stored.stack().getHoverName().getString() : stored.itemId();
    }

    /** {@code extra} goes after the price, seller and time lines. */
    static ItemStack listing(Listing listing, long now, Component... extra) {
        List<Component> lines = new ArrayList<>();
        lines.add(Buttons.plain(Text.translatable("markettrade.price_lore", showWithTotal(listing.price())),
                ChatFormatting.GOLD));
        lines.add(Buttons.line("markettrade.seller_lore", listing.sellerName()));
        lines.add(Buttons.line("markettrade.time_left_lore", TimeLeft.until(listing.expiresAt(), now)));
        List<Component> all = new ArrayList<>(lines);
        all.addAll(List.of(extra));

        if (!listing.item().isAvailable()) {
            ItemStack barrier = Buttons.of(Items.BARRIER, Component.literal(listing.item().itemId()));
            Buttons.addLore(barrier, List.of(Buttons.plain(Text.translatable("markettrade.unavailable_lore"),
                    ChatFormatting.RED)));
            Buttons.addLore(barrier, all);
            return barrier;
        }
        ItemStack stack = listing.item().stack();
        Buttons.addLore(stack, all);
        return stack;
    }

    /** A Returns entry: the stack itself, or a barrier when it can't load. */
    static ItemStack returned(StoredStack stored, Component... extra) {
        if (!stored.isAvailable()) {
            ItemStack barrier = Buttons.of(Items.BARRIER, Component.literal(stored.itemId()));
            Buttons.addLore(barrier, List.of(Buttons.plain(Text.translatable("markettrade.unavailable_lore"),
                    ChatFormatting.RED)));
            return barrier;
        }
        ItemStack stack = stored.stack();
        Buttons.addLore(stack, List.of(extra));
        return stack;
    }
}
