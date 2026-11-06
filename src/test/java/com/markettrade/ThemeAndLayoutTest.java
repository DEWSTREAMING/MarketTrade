package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.markettrade.client.ClientConfig;
import com.markettrade.client.Layout;
import com.markettrade.client.theme.Theme;
import com.markettrade.client.theme.Theme.Token;
import com.markettrade.client.theme.Themes;

class ThemeAndLayoutTest {
    @Test
    void rowCountComesFromTheHeightAt480By270() {
        Layout layout = Layout.compute(480, 270);
        // 270 - header 24 - pad 6 - column head 12 = 228 down to 270 - footer 20 - pad 6 = 244, so 202 units of 24 each.
        assertEquals(8, layout.rows());
        assertTrue(layout.listTop() + layout.rows() * layout.rowStep() <= layout.footerTop());
        assertTrue(layout.sideW() > 0, "480 wide still has the category column");
        assertTrue(layout.listingRows() >= 3);
    }

    @Test
    void biggerWindowsGetMoreRowsAndNeverZero() {
        assertTrue(Layout.compute(960, 540).rows() > Layout.compute(640, 360).rows());
        assertTrue(Layout.compute(640, 360).rows() > Layout.compute(480, 270).rows());
        assertEquals(1, Layout.compute(200, 60).rows());
        assertEquals(1, Layout.compute(10, 10).listingRows());
    }

    @Test
    void rowsNeverRunIntoTheFooter() {
        for (int h = 100; h <= 1100; h += 7) {
            Layout layout = Layout.compute(480, h);
            if (h > 130) {
                assertTrue(layout.listTop() + layout.rows() * layout.rowStep() - Layout.ROW_GAP
                        <= layout.footerTop(), "height " + h);
            }
        }
    }

    @Test
    void narrowWindowsCollapseTheCategoryColumn() {
        Layout narrow = Layout.compute(320, 240);
        assertTrue(narrow.collapsed());
        assertEquals(0, narrow.sideW());
        assertTrue(!Layout.compute(960, 540).collapsed());
    }

    @Test
    void everyThemeHasEveryColour() {
        assertEquals(4, Themes.all().size());
        for (Theme theme : Themes.all()) {
            for (Token token : Token.values()) {
                Integer colour = theme.colours().get(token);
                assertNotNull(colour, theme.id() + " " + token);
                if (token != Token.SHADE) {
                    assertEquals(0xFF, colour >>> 24, theme.id() + " " + token + " should be solid");
                }
            }
            assertTrue(theme.nameKey().startsWith("markettrade.theme."));
        }
    }

    @Test
    void themeIdsAreUnique() {
        long distinct = Themes.all().stream().map(Theme::id).distinct().count();
        assertEquals(Themes.all().size(), distinct);
        assertSame(Themes.MARKET, Themes.find(Themes.DEFAULT_ID));
    }

    private static double luminance(int argb) {
        double[] c = { (argb >> 16 & 0xFF) / 255.0, (argb >> 8 & 0xFF) / 255.0, (argb & 0xFF) / 255.0 };
        for (int i = 0; i < 3; i++) {
            c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @Test
    void textIsReadableOnEveryTheme() {
        Token[][] pairs = {
                { Token.TEXT, Token.MAIN_BG }, { Token.TEXT, Token.ROW_BG }, { Token.TEXT, Token.ROW_HOVER },
                { Token.TEXT_DIM, Token.MAIN_BG }, { Token.TEXT_DIM, Token.ROW_BG },
                { Token.PRICE, Token.MAIN_BG }, { Token.PRICE, Token.ROW_BG }, { Token.SPECIAL, Token.ROW_BG },
                { Token.CHART_TEXT, Token.CHART_BG },
                { Token.HEADER_TEXT, Token.HEADER_BG }, { Token.HEADER_PRICE, Token.HEADER_BG },
                { Token.TITLE, Token.HEADER_BG },
                { Token.SIDE_TEXT, Token.SIDE_BG }, { Token.SIDE_DIM, Token.SIDE_BG },
                { Token.SIDE_SEL_TEXT, Token.SIDE_SEL_BG },
                { Token.BUTTON_TEXT, Token.BUTTON_BG }, { Token.BUTTON_TEXT, Token.BUTTON_HOVER },
                { Token.TAB_ON_TEXT, Token.TAB_ON_BG }, { Token.TAB_OFF_TEXT, Token.TAB_OFF_BG },
                { Token.BUY_TEXT, Token.BUY_BG }, { Token.SHORT_TEXT, Token.SHORT_BG },
                { Token.INPUT_TEXT, Token.INPUT_BG }, { Token.DIALOG_TEXT, Token.DIALOG_BG },
                { Token.NOTE_OK, Token.MAIN_BG }, { Token.NOTE_BAD, Token.MAIN_BG },
        };
        StringBuilder weak = new StringBuilder();
        for (Theme theme : Themes.all()) {
            for (Token[] pair : pairs) {
                double ratio = contrast(theme.c(pair[0]), theme.c(pair[1]));
                if (ratio < 4.5) {
                    weak.append(theme.id()).append(' ').append(pair[0]).append(" on ").append(pair[1]).append(" is only ")
                            .append(String.format("%.2f", ratio)).append(';');
                }
            }
        }
        assertEquals("", weak.toString());
    }

    @Test
    void aBadThemeIdFallsBackToMarket() {
        assertSame(Themes.MARKET, ClientConfig.parseTheme("{\"theme\":\"neon\"}"));
        assertSame(Themes.MARKET, ClientConfig.parseTheme("not json {"));
        assertSame(Themes.MARKET, ClientConfig.parseTheme("[1,2]"));
        assertSame(Themes.MARKET, ClientConfig.parseTheme("{\"theme\":5}"));
        assertSame(Themes.MARKET, ClientConfig.parseTheme("{\"theme\":{}}"));
        assertSame(Themes.MARKET, ClientConfig.parseTheme(""));
        assertSame(Themes.MARKET, ClientConfig.parseTheme(null));
        assertSame(Themes.MARKET, ClientConfig.parseTheme("{}"));
    }

    @Test
    void everyThemeSurvivesWritingAndReading() {
        for (Theme theme : Themes.all()) {
            assertSame(theme, ClientConfig.parseTheme(ClientConfig.toJson(theme)));
        }
        List<String> ids = Themes.all().stream().map(Theme::id).toList();
        assertEquals(List.of("market", "trading_post", "vanilla_plus", "deepslate"), ids);
    }
}
