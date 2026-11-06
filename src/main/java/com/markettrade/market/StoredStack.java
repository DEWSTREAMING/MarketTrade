package com.markettrade.market;

import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A stack held by the market. Either it loaded fully, or it didn't (for example its mod was removed) and the raw saved tag is
 * kept to be written back unchanged. Never drop an unavailable one.
 */
public final class StoredStack {
    private final ItemStack stack;
    private final CompoundTag raw;

    private StoredStack(ItemStack stack, CompoundTag raw) {
        this.stack = stack;
        this.raw = raw;
    }

    public static StoredStack of(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Can't store an empty stack");
        }
        return new StoredStack(stack.copy(), null);
    }

    public static StoredStack unavailable(CompoundTag raw) {
        return new StoredStack(null, raw.copy());
    }

    /**
     * {@code ItemStack.parse} would hand back a half loaded stack with only a log line to show for it. The codec result is
     * empty unless the whole stack loaded.
     */
    public static StoredStack fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        Optional<ItemStack> parsed = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result();
        if (parsed.isPresent() && !parsed.get().isEmpty()) {
            return new StoredStack(parsed.get(), null);
        }
        return unavailable(tag);
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        if (stack == null) {
            return raw.copy();
        }
        Tag saved = stack.save(registries);
        return (CompoundTag) saved;
    }

    public boolean isAvailable() {
        return stack != null;
    }

    /** A copy of the stack. Only for an available one. */
    public ItemStack stack() {
        if (stack == null) {
            throw new IllegalStateException("This stack is unavailable");
        }
        return stack.copy();
    }

    /** True if both hold the same thing: equal stacks (data and count) or equal raw tags. */
    public boolean sameAs(StoredStack other) {
        if (other == null) {
            return false;
        }
        if (stack != null && other.stack != null) {
            return ItemStack.matches(stack, other.stack);
        }
        if (stack == null && other.stack == null) {
            return raw.equals(other.raw);
        }
        return false;
    }

    public CompoundTag raw() {
        return raw == null ? null : raw.copy();
    }

    /** The item id, also for an unavailable stack (read from the raw tag). */
    public String itemId() {
        if (stack != null) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return raw.getString("id");
    }

    public int count() {
        return stack != null ? stack.getCount() : raw.getInt("count");
    }

    /** The same stack with fewer items. Only for an available one, and the count must stay above zero. */
    public StoredStack withCount(int count) {
        ItemStack smaller = stack();
        smaller.setCount(count);
        return of(smaller);
    }
}
