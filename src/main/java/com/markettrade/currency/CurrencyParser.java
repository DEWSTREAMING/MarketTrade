package com.markettrade.currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class CurrencyParser {
    public static final String DEFAULT_JSON = """
            {
              "name": "diamonds",
              "items": [
                { "item": "minecraft:diamond", "value": 1, "name": "diamond" },
                { "item": "minecraft:diamond_block", "value": 9, "name": "diamond block" }
              ]
            }
            """;

    /** The config to use, plus one message per problem found. Messages are for the log. */
    public record Result(CurrencyConfig config, List<String> errors) {
    }

    private CurrencyParser() {
    }

    public static CurrencyConfig defaultConfig() {
        return parse(DEFAULT_JSON, id -> true).config();
    }

    public static Result parse(String json, Predicate<String> itemExists) {
        List<String> errors = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                errors.add("currency.json must be a JSON object. No currency items will be used.");
                return new Result(CurrencyConfig.noItems(), errors);
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            errors.add("currency.json could not be read (" + e.getMessage() + "). No currency items will be used.");
            return new Result(CurrencyConfig.noItems(), errors);
        }

        String name = CurrencyConfig.FALLBACK_NAME;
        JsonElement nameElement = root.get("name");
        if (nameElement != null && nameElement.isJsonPrimitive() && !nameElement.getAsString().isBlank()) {
            name = nameElement.getAsString();
        } else {
            errors.add("currency.json has no \"name\". Using \"" + name + "\".");
        }

        JsonElement itemsElement = root.get("items");
        if (itemsElement == null || !itemsElement.isJsonArray()) {
            errors.add("currency.json has no \"items\" list. No currency items will be used.");
            return new Result(new CurrencyConfig(CurrencyConfig.FALLBACK_NAME, List.of()), errors);
        }
        if (itemsElement.getAsJsonArray().isEmpty()) {
            return new Result(new CurrencyConfig(name, List.of()), errors);
        }

        List<CurrencyItem> valid = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Map<Long, String> seenValues = new HashMap<>();
        int index = 0;
        for (JsonElement element : itemsElement.getAsJsonArray()) {
            index++;
            String label = "items entry " + index;
            if (!element.isJsonObject()) {
                errors.add(label + " is not an object. Skipped.");
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String id = readString(entry, "item");
            if (id == null) {
                errors.add(label + " has no \"item\". Skipped.");
                continue;
            }
            label = "item \"" + id + "\"";
            Long value = readValue(entry.get("value"));
            if (value == null || value <= 0) {
                errors.add(label + " needs a whole \"value\" above zero. Skipped.");
                continue;
            }
            if (!itemExists.test(id)) {
                errors.add(label + " is not an item on this server. Skipped.");
                continue;
            }
            if (!seenIds.add(id)) {
                errors.add(label + " is listed twice. Keeping the first one.");
                continue;
            }
            if (seenValues.containsKey(value)) {
                errors.add(label + " has the same value (" + value + ") as \"" + seenValues.get(value)
                        + "\". Skipped, the first one stays.");
                continue;
            }
            String shown = readString(entry, "name");
            if (shown == null) {
                int colon = id.indexOf(':');
                shown = colon >= 0 ? id.substring(colon + 1) : id;
                errors.add(label + " has no \"name\". Using \"" + shown + "\".");
            }
            seenValues.put(value, id);
            valid.add(new CurrencyItem(id, value, shown));
        }

        if (valid.isEmpty()) {
            errors.add("No valid currency items are left. No currency items will be used.");
            return new Result(CurrencyConfig.noItems(), errors);
        }
        return new Result(new CurrencyConfig(name, valid), errors);
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = element.getAsString();
        return text.isBlank() ? null : text;
    }

    private static Long readValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(element.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
