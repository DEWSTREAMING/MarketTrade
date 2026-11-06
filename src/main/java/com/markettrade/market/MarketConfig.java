package com.markettrade.market;

import java.util.List;

/** Owner settings for the market. No Minecraft classes. */
public record MarketConfig(int cutPercent, long listingDays, int maxListingsPerPlayer, List<String> blockedWords,
        int reportCooldownSeconds) {
    public static final int DEFAULT_CUT_PERCENT = 5;
    public static final long DEFAULT_LISTING_DAYS = 7;
    public static final int DEFAULT_MAX_LISTINGS = 20;
    public static final int DEFAULT_REPORT_COOLDOWN = 30;

    public MarketConfig {
        blockedWords = List.copyOf(blockedWords);
    }

    public MarketConfig(int cutPercent, long listingDays, int maxListingsPerPlayer) {
        this(cutPercent, listingDays, maxListingsPerPlayer, List.of(), DEFAULT_REPORT_COOLDOWN);
    }

    public static MarketConfig defaults() {
        return new MarketConfig(DEFAULT_CUT_PERCENT, DEFAULT_LISTING_DAYS, DEFAULT_MAX_LISTINGS);
    }
}
