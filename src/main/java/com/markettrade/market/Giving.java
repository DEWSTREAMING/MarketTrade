package com.markettrade.market;

import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The one way to hand a stack to a player. Everything that gives items uses this. */
public final class Giving {
    private Giving() {
    }

    /**
     * Tries to put the stack in the inventory and returns how many items really arrived. The stack passed in is not changed.
     * {@code Inventory.add} can't be trusted for this: it returns true on a partial add, and a creative player's stack that
     * doesn't fit is discarded and reported as added. So the matching items are counted before and after. The offhand
     * counts too, because {@code add} tries it early, and missing it would count merged items as not given.
     */
    public static int give(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack copy = stack.copy();
        int before = countMatching(inventory, copy);
        inventory.add(copy);
        int given = countMatching(inventory, stack) - before;
        return Math.max(0, Math.min(given, stack.getCount()));
    }

    private static int countMatching(Inventory inventory, ItemStack like) {
        return countMatching(inventory.items, like) + countMatching(inventory.offhand, like);
    }

    private static int countMatching(List<ItemStack> stacks, ItemStack like) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, like)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
