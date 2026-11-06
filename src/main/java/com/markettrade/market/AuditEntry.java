package com.markettrade.market;

import java.util.Locale;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * One line of the audit log. Written with Gson, so quotes and newlines in a name stay inside their string and can't fake or break
 * another entry. No Minecraft classes.
 */
public final class AuditEntry {
    public static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    private final long time;
    private final String event;
    private UUID actor;
    private String actorName;
    private UUID target;
    private String targetName;
    private Long listing;
    private Long price;
    private Long cut;
    private String reason;
    private AuditItem item;

    public AuditEntry(long time, String event) {
        this.time = time;
        this.event = event;
    }

    public AuditEntry actor(UUID id, String name) {
        this.actor = id;
        this.actorName = name;
        return this;
    }

    public AuditEntry actor(Actor who) {
        return actor(who.id(), who.name());
    }

    public AuditEntry target(UUID id, String name) {
        this.target = id;
        this.targetName = name;
        return this;
    }

    public AuditEntry listing(long id) {
        this.listing = id;
        return this;
    }

    public AuditEntry price(long price) {
        this.price = price;
        return this;
    }

    public AuditEntry cut(long cut) {
        this.cut = cut;
        return this;
    }

    public AuditEntry reason(String reason) {
        this.reason = reason;
        return this;
    }

    public AuditEntry item(AuditItem item) {
        this.item = item;
        return this;
    }

    public long time() {
        return time;
    }

    public String event() {
        return event;
    }

    public UUID actor() {
        return actor;
    }

    public String actorName() {
        return actorName;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    /** Null if the entry has no listing. */
    public Long listing() {
        return listing;
    }

    public Long price() {
        return price;
    }

    public Long cut() {
        return cut;
    }

    public String reason() {
        return reason;
    }

    public AuditItem item() {
        return item;
    }

    public boolean involves(UUID player) {
        return player.equals(actor) || player.equals(target);
    }

    /** True if the item's shown name or any of its texts contains {@code needle}, ignoring case. */
    public boolean itemTextContains(String needle) {
        if (item == null) {
            return false;
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        if (item.name() != null && item.name().toLowerCase(Locale.ROOT).contains(lower)) {
            return true;
        }
        for (String text : item.texts()) {
            if (text.toLowerCase(Locale.ROOT).contains(lower)) {
                return true;
            }
        }
        return false;
    }

    /** One line, no line break in it. */
    public String toLine() {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("time", time);
        json.addProperty("event", event);
        if (actor != null) {
            json.addProperty("actor", actor.toString());
        }
        if (actorName != null) {
            json.addProperty("actorName", actorName);
        }
        if (target != null) {
            json.addProperty("target", target.toString());
        }
        if (targetName != null) {
            json.addProperty("targetName", targetName);
        }
        if (listing != null) {
            json.addProperty("listing", listing);
        }
        if (price != null) {
            json.addProperty("price", price);
        }
        if (cut != null) {
            json.addProperty("cut", cut);
        }
        if (reason != null) {
            json.addProperty("reason", reason);
        }
        if (item != null) {
            json.add("item", item.toJson());
        }
        return GSON.toJson(json);
    }

    /** Throws IllegalArgumentException for a line that isn't a valid entry. */
    public static AuditEntry fromLine(String line) {
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("not a JSON object");
            }
            JsonObject json = parsed.getAsJsonObject();
            if (!json.has("time") || !json.has("event")) {
                throw new IllegalArgumentException("no time or event");
            }
            AuditEntry entry = new AuditEntry(json.get("time").getAsLong(), json.get("event").getAsString());
            if (json.has("actor")) {
                entry.actor = UUID.fromString(json.get("actor").getAsString());
            }
            entry.actorName = text(json, "actorName");
            if (json.has("target")) {
                entry.target = UUID.fromString(json.get("target").getAsString());
            }
            entry.targetName = text(json, "targetName");
            if (json.has("listing")) {
                entry.listing = json.get("listing").getAsLong();
            }
            if (json.has("price")) {
                entry.price = json.get("price").getAsLong();
            }
            if (json.has("cut")) {
                entry.cut = json.get("cut").getAsLong();
            }
            entry.reason = text(json, "reason");
            if (json.has("item") && json.get("item").isJsonObject()) {
                entry.item = AuditItem.fromJson(json.getAsJsonObject("item"));
            }
            return entry;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("bad audit line: " + e.getMessage(), e);
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
