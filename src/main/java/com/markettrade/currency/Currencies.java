package com.markettrade.currency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

/** Holds the currency in use and reads it from config/markettrade/currency.json. */
public final class Currencies {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile CurrencyConfig current = CurrencyConfig.noItems();

    private Currencies() {
    }

    public static CurrencyConfig current() {
        return current;
    }

    public static void set(CurrencyConfig config) {
        current = config;
    }

    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("markettrade").resolve("currency.json");
    }

    /** Needs the item registry, so call it once the server is about to start. Returns the number of problems found. */
    public static int load() {
        Path file = configFile();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, CurrencyParser.DEFAULT_JSON, StandardCharsets.UTF_8);
                LOGGER.info("Wrote the default currency config to {}", file);
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            CurrencyParser.Result result = CurrencyParser.parse(json, Currencies::itemExists);
            result.errors().forEach(message -> LOGGER.error("Currency config: {}", message));
            current = result.config();
            LOGGER.info("Currency is \"{}\" with {} item(s)", current.name(), current.items().size());
            return result.errors().size();
        } catch (IOException e) {
            LOGGER.error("Currency config {} could not be read or written. No currency items will be used.", file, e);
            current = CurrencyConfig.noItems();
            return 1;
        }
    }

    private static boolean itemExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null
                && BuiltInRegistries.ITEM.containsKey(location)
                && BuiltInRegistries.ITEM.get(location) != Items.AIR;
    }
}
