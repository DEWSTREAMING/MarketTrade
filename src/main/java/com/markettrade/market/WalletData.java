package com.markettrade.market;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class WalletData extends SavedData {
    public static final String NAME = "markettrade_wallets";
    /** Raise this and add an upgrade step in {@link #upgrade} (with a test) whenever the saved layout changes. */
    public static final int FORMAT_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERSION_KEY = "version";
    private static final String WALLETS_KEY = "wallets";

    private final Wallets wallets = new Wallets();

    public WalletData() {
    }

    public static SavedData.Factory<WalletData> factory() {
        return new SavedData.Factory<>(WalletData::new, WalletData::load, null);
    }

    /** Throws on a newer version or broken data. Vanilla swallows that, and the caller at server start deals with it. */
    public static WalletData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(VERSION_KEY, Tag.TAG_INT)) {
            throw new IllegalStateException("Wallet data has no format version");
        }
        int version = tag.getInt(VERSION_KEY);
        if (version > FORMAT_VERSION) {
            throw new IllegalStateException("Wallet data is format " + version + " but this mod only knows up to "
                    + FORMAT_VERSION + ". Use a newer Market Trade.");
        }
        if (version < 1) {
            throw new IllegalStateException("Wallet data has an invalid format version " + version);
        }
        tag = upgrade(tag, version);
        if (!tag.contains(WALLETS_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException("Wallet data has no wallet list");
        }
        WalletData data = new WalletData();
        ListTag list = tag.getList(WALLETS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID id = UUID.fromString(entry.getString("player"));
                if (!entry.contains("balance", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("no balance");
                }
                long balance = entry.getLong("balance");
                if (!data.wallets.set(id, balance)) {
                    throw new IllegalArgumentException("negative balance " + balance);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping wallet entry {} ({}): {}", i, entry, e.getMessage());
            }
        }
        return data;
    }

    /** Brings older data up to {@link #FORMAT_VERSION}. Nothing to do yet, version 1 is the first. */
    private static CompoundTag upgrade(CompoundTag tag, int fromVersion) {
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, FORMAT_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : wallets.entries().entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("player", entry.getKey().toString());
            item.putLong("balance", entry.getValue());
            list.add(item);
        }
        tag.put(WALLETS_KEY, list);
        return tag;
    }

    public static WalletData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public long balance(UUID player) {
        return wallets.balance(player);
    }

    public boolean canAdd(UUID player, long amount) {
        return wallets.canAdd(player, amount);
    }

    public boolean add(UUID player, long amount) {
        boolean ok = wallets.add(player, amount);
        if (ok) {
            setDirty();
        }
        return ok;
    }

    public boolean take(UUID player, long amount) {
        boolean ok = wallets.take(player, amount);
        if (ok) {
            setDirty();
        }
        return ok;
    }

    public boolean set(UUID player, long amount) {
        boolean ok = wallets.set(player, amount);
        if (ok) {
            setDirty();
        }
        return ok;
    }
}
