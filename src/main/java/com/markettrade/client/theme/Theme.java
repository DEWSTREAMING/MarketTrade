package com.markettrade.client.theme;

import java.util.EnumMap;
import java.util.Map;

/**
 * Colours and panel shapes for the client screen. Every colour the screen draws is looked up here, so a theme never changes what is
 * on screen or where.
 */
public record Theme(String id, String nameKey, PanelStyle panel, RowStyle rows, boolean iconSlots, Map<Token, Integer> colours) {
    public enum PanelStyle {
        FLAT, BEVEL
    }

    public enum RowStyle {
        /** Each row is its own filled block. */
        CARD,
        /** Rows share one surface and are separated by thin lines, like a ledger. */
        LEDGER
    }

    public enum Token {
        BG, HEADER_BG, HEADER_LINE, TITLE, HEADER_TEXT, HEADER_PRICE,
        SIDE_BG, SIDE_LINE, SIDE_TEXT, SIDE_DIM, SIDE_SEL_BG, SIDE_SEL_TEXT,
        MAIN_BG, MAIN_LINE, TEXT, TEXT_DIM, PRICE, SPECIAL, ROW_BG, ROW_HOVER, ROW_LINE,
        CHART_BG, CHART_TEXT, CHART_BAR, CHART_AXIS,
        BUTTON_BG, BUTTON_TEXT, BUTTON_EDGE, BUTTON_HOVER,
        TAB_ON_BG, TAB_ON_TEXT, TAB_ON_EDGE, TAB_OFF_BG, TAB_OFF_TEXT, TAB_OFF_EDGE,
        BUY_BG, BUY_TEXT, BUY_EDGE, SHORT_BG, SHORT_TEXT, SHORT_EDGE,
        INPUT_BG, INPUT_TEXT, INPUT_EDGE,
        DIALOG_BG, DIALOG_TEXT, DIALOG_EDGE,
        BEVEL_LIGHT, BEVEL_DARK, SHADE, NOTE_OK, NOTE_BAD
    }

    public Theme {
        colours = Map.copyOf(colours);
    }

    /** The colour as 0xAARRGGBB. */
    public int c(Token token) {
        return colours.get(token);
    }

    public static Builder builder(String id, PanelStyle panel, RowStyle rows, boolean iconSlots) {
        return new Builder(id, panel, rows, iconSlots);
    }

    public static final class Builder {
        private final String id;
        private final PanelStyle panel;
        private final RowStyle rows;
        private final boolean iconSlots;
        private final Map<Token, Integer> map = new EnumMap<>(Token.class);

        private Builder(String id, PanelStyle panel, RowStyle rows, boolean iconSlots) {
            this.id = id;
            this.panel = panel;
            this.rows = rows;
            this.iconSlots = iconSlots;
        }

        /** {@code rgb} is 0xRRGGBB, drawn solid. */
        public Builder set(Token token, int rgb) {
            map.put(token, 0xFF000000 | rgb);
            return this;
        }

        public Builder setArgb(Token token, int argb) {
            map.put(token, argb);
            return this;
        }

        public Theme build() {
            for (Token token : Token.values()) {
                if (!map.containsKey(token)) {
                    throw new IllegalStateException("Theme " + id + " has no colour for " + token);
                }
            }
            return new Theme(id, "markettrade.theme." + id, panel, rows, iconSlots, map);
        }
    }
}
