package com.markettrade.market;

/** The ways the Browse page can order listings. */
public enum SortMode {
    NEWEST("newest"),
    CHEAPEST("cheapest"),
    DEAREST("dearest"),
    ENDING_SOON("ending_soon"),
    NAME("name");

    private final String key;

    SortMode(String key) {
        this.key = key;
    }

    /** Suffix of the lang key that names this mode. */
    public String key() {
        return key;
    }

    public SortMode next() {
        SortMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
