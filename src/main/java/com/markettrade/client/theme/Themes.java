package com.markettrade.client.theme;

import java.util.List;

import com.markettrade.client.theme.Theme.PanelStyle;
import com.markettrade.client.theme.Theme.RowStyle;
import com.markettrade.client.theme.Theme.Token;

/** The four looks. Colours come from the design mockups in docs/design/mockups. */
public final class Themes {
    public static final String DEFAULT_ID = "market";

    /** Dark blue, flat. From Main and ItemPage. */
    public static final Theme MARKET = Theme.builder("market", PanelStyle.FLAT, RowStyle.CARD, false)
            .set(Token.BG, 0x141a22).set(Token.HEADER_BG, 0x0d1218).set(Token.HEADER_LINE, 0x2f3d4c)
            .set(Token.TITLE, 0xf2c14e).set(Token.HEADER_TEXT, 0xb6c2cd).set(Token.HEADER_PRICE, 0xf2c14e)
            .set(Token.SIDE_BG, 0x10161d).set(Token.SIDE_LINE, 0x2f3d4c).set(Token.SIDE_TEXT, 0xcfd8e0)
            .set(Token.SIDE_DIM, 0xb6c2cd).set(Token.SIDE_SEL_BG, 0x2f3d4c).set(Token.SIDE_SEL_TEXT, 0xffffff)
            .set(Token.MAIN_BG, 0x141a22).set(Token.MAIN_LINE, 0x2f3d4c).set(Token.TEXT, 0xe4e9ee)
            .set(Token.TEXT_DIM, 0xb6c2cd).set(Token.PRICE, 0xf2c14e).set(Token.SPECIAL, 0xd9a6ff)
            .set(Token.ROW_BG, 0x1c2530).set(Token.ROW_HOVER, 0x27323f).set(Token.ROW_LINE, 0x2f3d4c)
            .set(Token.CHART_BG, 0x1c2530).set(Token.CHART_TEXT, 0xb6c2cd).set(Token.CHART_BAR, 0x6fb35a).set(Token.CHART_AXIS, 0x5b7188)
            .set(Token.BUTTON_BG, 0x2f3d4c).set(Token.BUTTON_TEXT, 0xffffff).set(Token.BUTTON_EDGE, 0x5b7188)
            .set(Token.BUTTON_HOVER, 0x3d4f63)
            .set(Token.TAB_ON_BG, 0x2f3d4c).set(Token.TAB_ON_TEXT, 0xffffff).set(Token.TAB_ON_EDGE, 0x5b7188)
            .set(Token.TAB_OFF_BG, 0x0d1218).set(Token.TAB_OFF_TEXT, 0xb6c2cd).set(Token.TAB_OFF_EDGE, 0x2f3d4c)
            .set(Token.BUY_BG, 0x4a8036).set(Token.BUY_TEXT, 0xffffff).set(Token.BUY_EDGE, 0x86c26f)
            .set(Token.SHORT_BG, 0x232c37).set(Token.SHORT_TEXT, 0x9fb0c0).set(Token.SHORT_EDGE, 0x3d4b5b)
            .set(Token.INPUT_BG, 0x000000).set(Token.INPUT_TEXT, 0xffffff).set(Token.INPUT_EDGE, 0x5b7188)
            .set(Token.DIALOG_BG, 0x1c2530).set(Token.DIALOG_TEXT, 0xe4e9ee).set(Token.DIALOG_EDGE, 0x5b7188)
            .set(Token.BEVEL_LIGHT, 0x5b7188).set(Token.BEVEL_DARK, 0x0d1218)
            .setArgb(Token.SHADE, 0xA0000000).set(Token.NOTE_OK, 0x8fd98a).set(Token.NOTE_BAD, 0xff8f85)
            .build();

    /** Oak and parchment, flat, rows as ledger lines. From OakBrowse and OakItem. */
    public static final Theme TRADING_POST = Theme.builder("trading_post", PanelStyle.FLAT, RowStyle.LEDGER, false)
            .set(Token.BG, 0x3b2a1c).set(Token.HEADER_BG, 0x2a1c11).set(Token.HEADER_LINE, 0x2a1c11)
            .set(Token.TITLE, 0xf0cf7a).set(Token.HEADER_TEXT, 0xcdbb9f).set(Token.HEADER_PRICE, 0xf0cf7a)
            .set(Token.SIDE_BG, 0x5a4029).set(Token.SIDE_LINE, 0xa08560).set(Token.SIDE_TEXT, 0xeadfc8)
            .set(Token.SIDE_DIM, 0xeadfc8).set(Token.SIDE_SEL_BG, 0xe8d9b5).set(Token.SIDE_SEL_TEXT, 0x33261a)
            .set(Token.MAIN_BG, 0xe8d9b5).set(Token.MAIN_LINE, 0xa08560).set(Token.TEXT, 0x33261a)
            .set(Token.TEXT_DIM, 0x6a553d).set(Token.PRICE, 0x7a4a00).set(Token.SPECIAL, 0x7a2f8f)
            .set(Token.ROW_BG, 0xe8d9b5).set(Token.ROW_HOVER, 0xf3e8cc).set(Token.ROW_LINE, 0xb9a57e)
            .set(Token.CHART_BG, 0xe8d9b5).set(Token.CHART_TEXT, 0x6a553d).set(Token.CHART_BAR, 0xa06a2c).set(Token.CHART_AXIS, 0x5a4029)
            .set(Token.BUTTON_BG, 0x5a4029).set(Token.BUTTON_TEXT, 0xffffff).set(Token.BUTTON_EDGE, 0xa08560)
            .set(Token.BUTTON_HOVER, 0x6e5136)
            .set(Token.TAB_ON_BG, 0xe8d9b5).set(Token.TAB_ON_TEXT, 0x33261a).set(Token.TAB_ON_EDGE, 0xf0cf7a)
            .set(Token.TAB_OFF_BG, 0x5a4029).set(Token.TAB_OFF_TEXT, 0xeadfc8).set(Token.TAB_OFF_EDGE, 0x5a4029)
            .set(Token.BUY_BG, 0x4f7a32).set(Token.BUY_TEXT, 0xffffff).set(Token.BUY_EDGE, 0x8fb86f)
            .set(Token.SHORT_BG, 0xcdbb9f).set(Token.SHORT_TEXT, 0x5c4a35).set(Token.SHORT_EDGE, 0xa08560)
            .set(Token.INPUT_BG, 0x000000).set(Token.INPUT_TEXT, 0xffffff).set(Token.INPUT_EDGE, 0xa08560)
            .set(Token.DIALOG_BG, 0xe8d9b5).set(Token.DIALOG_TEXT, 0x33261a).set(Token.DIALOG_EDGE, 0x5a4029)
            .set(Token.BEVEL_LIGHT, 0xf3e8cc).set(Token.BEVEL_DARK, 0x5a4029)
            .setArgb(Token.SHADE, 0xA0000000).set(Token.NOTE_OK, 0x2f5d1a).set(Token.NOTE_BAD, 0x8a1c1c)
            .build();

    /** Grey panels, bevelled, icons in slots. From VanillaBrowse and VanillaItem. */
    public static final Theme VANILLA_PLUS = Theme.builder("vanilla_plus", PanelStyle.BEVEL, RowStyle.CARD, true)
            .set(Token.BG, 0x3a4a33).set(Token.HEADER_BG, 0x3a4a33).set(Token.HEADER_LINE, 0x3a4a33)
            .set(Token.TITLE, 0xffffff).set(Token.HEADER_TEXT, 0xffffff).set(Token.HEADER_PRICE, 0xffe58a)
            .set(Token.SIDE_BG, 0xc6c6c6).set(Token.SIDE_LINE, 0x555555).set(Token.SIDE_TEXT, 0x111111)
            .set(Token.SIDE_DIM, 0x404040).set(Token.SIDE_SEL_BG, 0xa9a9a9).set(Token.SIDE_SEL_TEXT, 0x111111)
            .set(Token.MAIN_BG, 0xc6c6c6).set(Token.MAIN_LINE, 0x555555).set(Token.TEXT, 0x2a2a2a)
            .set(Token.TEXT_DIM, 0x404040).set(Token.PRICE, 0x5e3700).set(Token.SPECIAL, 0x5b1f7a)
            .set(Token.ROW_BG, 0xb0b0b0).set(Token.ROW_HOVER, 0xdcdcdc).set(Token.ROW_LINE, 0x8b8b8b)
            .set(Token.CHART_BG, 0x000000).set(Token.CHART_TEXT, 0xd0d0d0).set(Token.CHART_BAR, 0x55ff55).set(Token.CHART_AXIS, 0xa0a0a0)
            .set(Token.BUTTON_BG, 0x8b8b8b).set(Token.BUTTON_TEXT, 0x111111).set(Token.BUTTON_EDGE, 0xffffff)
            .set(Token.BUTTON_HOVER, 0xa9a9a9)
            .set(Token.TAB_ON_BG, 0xc6c6c6).set(Token.TAB_ON_TEXT, 0x2a2a2a).set(Token.TAB_ON_EDGE, 0xffffff)
            .set(Token.TAB_OFF_BG, 0x8b8b8b).set(Token.TAB_OFF_TEXT, 0x111111).set(Token.TAB_OFF_EDGE, 0x5a5a5a)
            .set(Token.BUY_BG, 0x4f7a38).set(Token.BUY_TEXT, 0xffffff).set(Token.BUY_EDGE, 0xa5d08f)
            .set(Token.SHORT_BG, 0x8b8b8b).set(Token.SHORT_TEXT, 0x1a1a1a).set(Token.SHORT_EDGE, 0x5a5a5a)
            .set(Token.INPUT_BG, 0x000000).set(Token.INPUT_TEXT, 0xffffff).set(Token.INPUT_EDGE, 0xa0a0a0)
            .set(Token.DIALOG_BG, 0xc6c6c6).set(Token.DIALOG_TEXT, 0x2a2a2a).set(Token.DIALOG_EDGE, 0xffffff)
            .set(Token.BEVEL_LIGHT, 0xffffff).set(Token.BEVEL_DARK, 0x373737)
            .setArgb(Token.SHADE, 0xA0000000).set(Token.NOTE_OK, 0x1f5d1a).set(Token.NOTE_BAD, 0x8a1c1c)
            .build();

    /** Dark grey and copper, flat. From SplitView, on the same layout as the others. */
    public static final Theme DEEPSLATE = Theme.builder("deepslate", PanelStyle.FLAT, RowStyle.CARD, false)
            .set(Token.BG, 0x1e1f24).set(Token.HEADER_BG, 0x15161a).set(Token.HEADER_LINE, 0xd98a5b)
            .set(Token.TITLE, 0xe9a877).set(Token.HEADER_TEXT, 0xc4c5cc).set(Token.HEADER_PRICE, 0xf2c14e)
            .set(Token.SIDE_BG, 0x191a1f).set(Token.SIDE_LINE, 0x3a3c44).set(Token.SIDE_TEXT, 0xe6e6ea)
            .set(Token.SIDE_DIM, 0xb4b5bd).set(Token.SIDE_SEL_BG, 0x34353d).set(Token.SIDE_SEL_TEXT, 0xffffff)
            .set(Token.MAIN_BG, 0x1e1f24).set(Token.MAIN_LINE, 0x3a3c44).set(Token.TEXT, 0xe6e6ea)
            .set(Token.TEXT_DIM, 0xb4b5bd).set(Token.PRICE, 0xf2c14e).set(Token.SPECIAL, 0xd9a6ff)
            .set(Token.ROW_BG, 0x2a2b31).set(Token.ROW_HOVER, 0x34353d).set(Token.ROW_LINE, 0x3a3c44)
            .set(Token.CHART_BG, 0x191a1f).set(Token.CHART_TEXT, 0xb4b5bd).set(Token.CHART_BAR, 0xd98a5b).set(Token.CHART_AXIS, 0x5c5e68)
            .set(Token.BUTTON_BG, 0x2a2b31).set(Token.BUTTON_TEXT, 0xffffff).set(Token.BUTTON_EDGE, 0x5c5e68)
            .set(Token.BUTTON_HOVER, 0x3a3c44)
            .set(Token.TAB_ON_BG, 0x3a3c44).set(Token.TAB_ON_TEXT, 0xffffff).set(Token.TAB_ON_EDGE, 0xd98a5b)
            .set(Token.TAB_OFF_BG, 0x15161a).set(Token.TAB_OFF_TEXT, 0xc4c5cc).set(Token.TAB_OFF_EDGE, 0x15161a)
            .set(Token.BUY_BG, 0x2b7a64).set(Token.BUY_TEXT, 0xffffff).set(Token.BUY_EDGE, 0x5fc2a5)
            .set(Token.SHORT_BG, 0x2a2b31).set(Token.SHORT_TEXT, 0xb4b5bd).set(Token.SHORT_EDGE, 0x5c5e68)
            .set(Token.INPUT_BG, 0x000000).set(Token.INPUT_TEXT, 0xffffff).set(Token.INPUT_EDGE, 0x5c5e68)
            .set(Token.DIALOG_BG, 0x2a2b31).set(Token.DIALOG_TEXT, 0xe6e6ea).set(Token.DIALOG_EDGE, 0xd98a5b)
            .set(Token.BEVEL_LIGHT, 0x5c5e68).set(Token.BEVEL_DARK, 0x15161a)
            .setArgb(Token.SHADE, 0xA0000000).set(Token.NOTE_OK, 0x7fe0a8).set(Token.NOTE_BAD, 0xff8f85)
            .build();

    private static final List<Theme> ALL = List.of(MARKET, TRADING_POST, VANILLA_PLUS, DEEPSLATE);

    private Themes() {
    }

    public static List<Theme> all() {
        return ALL;
    }

    /** The theme with this id, or null. */
    public static Theme find(String id) {
        if (id != null) {
            for (Theme theme : ALL) {
                if (theme.id().equals(id)) {
                    return theme;
                }
            }
        }
        return null;
    }
}
