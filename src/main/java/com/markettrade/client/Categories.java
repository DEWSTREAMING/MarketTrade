package com.markettrade.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * Categories are the creative tabs. A dedicated server doesn't fill them, so the client does it here, from its own tab contents. Items
 * in no tab (and everything from a mod that doesn't add one) fall under "Everything else". The map is by item id, built from every
 * display stack because a default stack of something like a potion is not {@code contains}-ed by its tab.
 */
public final class Categories {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Category(Component name, Set<String> itemIds) {
    }

    private static List<Category> tabs = List.of();
    private static boolean built;

    private Categories() {
    }

    public static void reset() {
        tabs = List.of();
        built = false;
    }

    public static List<Category> tabs() {
        ensure();
        return tabs;
    }

    public static void ensure() {
        if (built) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        built = true;
        try {
            // The same flag as the creative screen, so opening one doesn't make the other rebuild every tab.
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get(), mc.level.registryAccess());
            List<Category> found = new ArrayList<>();
            for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                    continue;
                }
                Set<String> ids = new HashSet<>();
                for (ItemStack stack : tab.getDisplayItems()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
                if (!ids.isEmpty()) {
                    found.add(new Category(tab.getDisplayName(), ids));
                }
            }
            tabs = found;
        } catch (RuntimeException e) {
            LOGGER.warn("Could not read the creative tabs. Everything will be under Everything else.", e);
            tabs = List.of();
        }
    }

    /** Name of the first tab that holds this item, for the item page. Empty if none. */
    public static String namesFor(String itemId) {
        for (Category category : tabs()) {
            if (category.itemIds().contains(itemId)) {
                return category.name().getString();
            }
        }
        return "";
    }
}
