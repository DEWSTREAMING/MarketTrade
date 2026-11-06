package com.markettrade.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.markettrade.client.theme.Theme;
import com.markettrade.client.theme.Themes;

/**
 * The player's own settings, in {@code config/markettrade-client.json}. Written only on the client. A pack can ship the file to
 * change the default look. No Minecraft classes, so the reading is tested on its own.
 */
public final class ClientConfig {
    private ClientConfig() {
    }

    /** The theme named in the text. A missing file, bad JSON or an unknown id gives Market, and the bad ones are logged. */
    public static Theme parseTheme(String json) {
        return parseTheme(json, message -> {
        });
    }

    /** {@code warn} gets one line for every problem found. */
    public static Theme parseTheme(String json, Consumer<String> warn) {
        if (json == null || json.isBlank()) {
            return Themes.MARKET;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                warn.accept("markettrade-client.json is not an object. Using the Market theme.");
                return Themes.MARKET;
            }
            JsonElement id = root.getAsJsonObject().get("theme");
            if (id == null || !id.isJsonPrimitive()) {
                return Themes.MARKET;
            }
            Theme theme = Themes.find(id.getAsString());
            if (theme == null) {
                warn.accept("Unknown theme '" + id.getAsString() + "' in markettrade-client.json. Using the Market theme.");
                return Themes.MARKET;
            }
            return theme;
        } catch (RuntimeException e) {
            warn.accept("Could not read markettrade-client.json (" + e.getMessage() + "). Using the Market theme.");
            return Themes.MARKET;
        }
    }

    public static String toJson(Theme theme) {
        JsonObject root = new JsonObject();
        root.addProperty("theme", theme.id());
        return root + "\n";
    }

    public static Theme read(Path file, Consumer<String> warn) {
        if (!Files.isRegularFile(file)) {
            return Themes.MARKET;
        }
        try {
            return parseTheme(Files.readString(file, StandardCharsets.UTF_8), warn);
        } catch (IOException e) {
            warn.accept("Could not read " + file + " (" + e.getMessage() + "). Using the Market theme.");
            return Themes.MARKET;
        }
    }

    /** Returns false if the file could not be written. */
    public static boolean write(Path file, Theme theme) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, toJson(theme), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
