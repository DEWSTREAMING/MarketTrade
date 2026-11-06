package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Every piece of player text in a stack: custom name, lore, book title, author and pages, and the same for whatever is inside
 * containers and bundles. Text a mod keeps in its own data is not found here, which is why the audit also keeps the SNBT.
 */
public final class ItemTexts {
    /** How deep to look into stacks inside stacks. */
    public static final int MAX_DEPTH = 6;

    private ItemTexts() {
    }

    public static List<String> collect(ItemStack stack) {
        List<String> texts = new ArrayList<>();
        collect(stack, texts, 0);
        return texts;
    }

    private static void collect(ItemStack stack, List<String> out, int depth) {
        if (stack.isEmpty() || depth > MAX_DEPTH) {
            return;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            add(out, stack.get(DataComponents.CUSTOM_NAME).getString());
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            lore.lines().forEach(line -> add(out, line.getString()));
        }
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            for (Filterable<String> page : writable.pages()) {
                add(out, page.raw());
                page.filtered().ifPresent(text -> add(out, text));
            }
        }
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            add(out, written.title().raw());
            written.title().filtered().ifPresent(text -> add(out, text));
            add(out, written.author());
            for (Filterable<net.minecraft.network.chat.Component> page : written.pages()) {
                add(out, page.raw().getString());
                page.filtered().ifPresent(text -> add(out, text.getString()));
            }
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.nonEmptyItems()) {
                collect(inner, out, depth + 1);
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) {
                collect(inner, out, depth + 1);
            }
        }
    }

    private static void add(List<String> out, String text) {
        if (text != null && !text.isEmpty()) {
            out.add(text);
        }
    }
}
