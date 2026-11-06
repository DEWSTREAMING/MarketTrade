package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The item part of an audit line. {@code data} is the whole stack as SNBT, or the raw saved tag for an unavailable one. */
public record AuditItem(String id, int count, String name, List<String> texts, String data) {
    public AuditItem {
        texts = texts == null ? List.of() : List.copyOf(texts);
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("count", count);
        json.addProperty("name", name);
        JsonArray array = new JsonArray();
        texts.forEach(array::add);
        json.add("texts", array);
        json.addProperty("data", data);
        return json;
    }

    static AuditItem fromJson(JsonObject json) {
        List<String> texts = new ArrayList<>();
        if (json.has("texts") && json.get("texts").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("texts")) {
                if (element.isJsonPrimitive()) {
                    texts.add(element.getAsString());
                }
            }
        }
        return new AuditItem(string(json, "id"), json.has("count") ? json.get("count").getAsInt() : 0,
                string(json, "name"), texts, string(json, "data"));
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
