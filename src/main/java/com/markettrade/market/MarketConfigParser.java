package com.markettrade.market;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class MarketConfigParser {
    public static final String DEFAULT_JSON = """
            {
              "cutPercent": 5,
              "listingDays": 7,
              "maxListingsPerPlayer": 20,
              "reportCooldownSeconds": 30,
              "blockedWords": [],
              "blockedWordsNote": "Matching ignores case, spaces and punctuation, and finds a word inside longer words too, so keep words long enough. A list never catches everything."
            }
            """;

    /** The config to use, plus one message per problem found. Messages are for the log. */
    public record Result(MarketConfig config, List<String> errors) {
    }

    private MarketConfigParser() {
    }

    public static Result parse(String json) {
        List<String> errors = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                errors.add("market.json must be a JSON object. Using all defaults.");
                return new Result(MarketConfig.defaults(), errors);
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            errors.add("market.json could not be read (" + e.getMessage() + "). Using all defaults.");
            return new Result(MarketConfig.defaults(), errors);
        }

        long cut = whole(root, "cutPercent", 0, 100, MarketConfig.DEFAULT_CUT_PERCENT, errors);
        long days = whole(root, "listingDays", 1, Long.MAX_VALUE, MarketConfig.DEFAULT_LISTING_DAYS, errors);
        long max = whole(root, "maxListingsPerPlayer", 1, Integer.MAX_VALUE, MarketConfig.DEFAULT_MAX_LISTINGS, errors);
        long cooldown = whole(root, "reportCooldownSeconds", 0, 86_400, MarketConfig.DEFAULT_REPORT_COOLDOWN, errors);
        List<String> blocked = words(root, errors);
        return new Result(new MarketConfig((int) cut, days, (int) max, blocked, (int) cooldown), errors);
    }

    private static List<String> words(JsonObject root, List<String> errors) {
        List<String> words = new ArrayList<>();
        JsonElement element = root.get("blockedWords");
        if (element == null || element.isJsonNull()) {
            return words;
        }
        if (!element.isJsonArray()) {
            errors.add("market.json \"blockedWords\" must be a list of words. Using none.");
            return words;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                words.add(entry.getAsString());
            } else {
                errors.add("market.json \"blockedWords\" has an entry that is not text. It was skipped.");
            }
        }
        return words;
    }

    /** Reads a whole number in range. A missing key is fine and silent, anything else wrong logs and uses the default. */
    private static long whole(JsonObject root, String key, long min, long max, long fallback, List<String> errors) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add("market.json \"" + key + "\" must be a whole number. Using " + fallback + ".");
            return fallback;
        }
        BigInteger value;
        try {
            BigDecimal decimal = element.getAsBigDecimal();
            value = decimal.toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add("market.json \"" + key + "\" must be a whole number. Using " + fallback + ".");
            return fallback;
        }
        if (value.compareTo(BigInteger.valueOf(min)) < 0) {
            errors.add("market.json \"" + key + "\" must be at least " + min + ". Using " + fallback + ".");
            return fallback;
        }
        if (value.compareTo(BigInteger.valueOf(max)) > 0) {
            errors.add("market.json \"" + key + "\" must be at most " + max + ". Using " + fallback + ".");
            return fallback;
        }
        return value.longValueExact();
    }
}
