package com.markettrade.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.loading.FMLPaths;

/** Holds the market settings in use and reads them from config/markettrade/market.json. */
public final class MarketConfigs {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile MarketConfig current = MarketConfig.defaults();

    private MarketConfigs() {
    }

    public static MarketConfig current() {
        return current;
    }

    public static void set(MarketConfig config) {
        current = config;
    }

    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("markettrade").resolve("market.json");
    }

    /** Returns the number of problems found. The file is written with defaults if it is missing, and never overwritten. */
    public static int load() {
        Path file = configFile();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, MarketConfigParser.DEFAULT_JSON, StandardCharsets.UTF_8);
                LOGGER.info("Wrote the default market config to {}", file);
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            MarketConfigParser.Result result = MarketConfigParser.parse(json);
            result.errors().forEach(message -> LOGGER.error("Market config: {}", message));
            current = result.config();
            LOGGER.info("Market cut is {}%, listings last {} day(s), {} listing(s) per player", current.cutPercent(),
                    current.listingDays(), current.maxListingsPerPlayer());
            return result.errors().size();
        } catch (IOException e) {
            LOGGER.error("Market config {} could not be read or written. Using the defaults.", file, e);
            current = MarketConfig.defaults();
            return 1;
        }
    }
}
