package com.markettrade.market;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Vanilla catches any error from a SavedData loader and then makes an empty object, which replaces the real file at the next
 * save. Call {@link #guardAtStart} for every SavedData this mod has, before anything uses it.
 */
public final class SavedDataGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SavedDataGuard() {
    }

    /** If the file is there but can't be loaded, it is copied aside first, so the empty data that follows can't destroy it. */
    public static <T extends SavedData> void guardAtStart(MinecraftServer server, SavedData.Factory<T> factory, String name) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        T loaded = storage.get(factory, name);
        if (loaded != null) {
            return;
        }
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(name + ".dat");
        if (!Files.exists(file)) {
            return;
        }
        try {
            Path copy = WalletBackup.copyAside(file, LocalDateTime.now());
            LOGGER.error("The saved data file {} could not be loaded. A copy was saved as {}. Starting with empty data.",
                    file, copy);
        } catch (IOException e) {
            LOGGER.error("The saved data file {} could not be loaded and could not be copied aside either.", file, e);
        }
    }
}
