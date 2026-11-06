package com.markettrade.market;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The blocked word check. Text and words are both reduced to lower case letters and digits, with formatting codes removed, and a
 * word matches if it appears anywhere inside the text. So a short word can match inside a longer one, and a list never catches
 * everything. No Minecraft classes.
 */
public final class BlockedWords {
    private static final char SECTION = '§';

    private BlockedWords() {
    }

    /** Removes formatting codes, lower cases, and keeps letters and digits only. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == SECTION) {
                i++;
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** The words as they will be compared. Words with nothing left after normalizing are dropped. */
    public static List<String> prepare(Collection<String> words) {
        List<String> prepared = new ArrayList<>();
        for (String word : words) {
            String normal = normalize(word);
            if (!normal.isEmpty()) {
                prepared.add(normal);
            }
        }
        return prepared;
    }

    /** The first listed word found in any of the texts, or null. {@code words} are the configured words, as written. */
    public static String find(Collection<String> texts, Collection<String> words) {
        List<String> prepared = prepare(words);
        if (prepared.isEmpty()) {
            return null;
        }
        for (String text : texts) {
            String normal = normalize(text);
            if (normal.isEmpty()) {
                continue;
            }
            for (String word : prepared) {
                if (normal.contains(word)) {
                    return word;
                }
            }
        }
        return null;
    }
}
