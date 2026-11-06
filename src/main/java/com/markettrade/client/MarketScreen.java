package com.markettrade.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.markettrade.client.theme.Theme;
import com.markettrade.client.theme.Theme.PanelStyle;
import com.markettrade.client.theme.Theme.RowStyle;
import com.markettrade.client.theme.Theme.Token;
import com.markettrade.client.theme.Themes;
import com.markettrade.market.ListingSort;
import com.markettrade.market.MarketViews;
import com.markettrade.market.SalesChart;
import com.markettrade.market.SummaryRow;
import com.markettrade.market.SummaryRows;
import com.markettrade.market.TimeLeft;
import com.markettrade.net.MarketPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The better market screen. It only shows what the server sent and sends requests back. Every rule (prices, who may buy, what a sale
 * does) is on the server. It is drawn by hand with hit regions, and every colour comes from the current {@link Theme}.
 */
public class MarketScreen extends Screen {
    private enum View {
        BROWSE, ITEM, MINE, RETURNS, WALLET, SELL
    }

    private enum Overlay {
        NONE, BUY, REPORT, THEME, CATEGORIES
    }

    private enum Kind {
        NORMAL, GOOD, SHORT, TAB_ON, TAB_OFF
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int OTHER = -2;
    private static final int ALL = -1;
    private static final long REPLY_SHOW_MS = 7000;
    private static final long PREVIEW_DELAY_MS = 220;

    private Layout lay;
    private View view = View.BROWSE;
    private Overlay overlay = Overlay.NONE;
    private final List<Hit> hits = new ArrayList<>();
    private ItemStack tooltip = ItemStack.EMPTY;

    private EditBox search;
    private EditBox price;
    private EditBox amount;
    private EditBox reason;

    // Browse
    private int category = ALL;
    /** First category shown in the side column and in the category list, and how many fit and exist at the last draw. */
    private int sideScroll;
    private int sideFit = 1;
    private int sideTotal;
    private int menuScroll;
    private int menuFit = 1;
    private int menuTotal;
    private SummaryRows.Sort sort = SummaryRows.Sort.CHEAPEST;
    private int browsePage;
    private List<SummaryRow> shownRows = List.of();
    private String shownKey = "";

    // Item page and the other lists
    private String itemId = "";
    private int itemPage;
    private int minePage;
    private int returnsPage;

    // Sell
    private int selectedSlot = -1;
    private ItemStack selected = ItemStack.EMPTY;
    private long previewDueAt;
    private boolean previewDirty;

    // Overlay targets
    private MarketViews.ListingRow target;

    private String requestedFor = "";
    private final Map<String, ItemStack> defaults = new HashMap<>();

    public MarketScreen() {
        super(Component.translatable("markettrade.ui.title"));
    }

    // Setup

    @Override
    protected void init() {
        lay = Layout.compute(width, height);
        Font f = font;
        if (search == null) {
            search = new EditBox(f, 0, 0, 80, 14, Component.translatable("markettrade.ui.search"));
            search.setMaxLength(50);
            search.setBordered(false);
            search.setHint(Component.translatable("markettrade.ui.search"));
            search.setResponder(text -> {
                browsePage = 0;
            });
            price = new EditBox(f, 0, 0, 80, 14, Component.translatable("markettrade.ui.price_hint"));
            price.setMaxLength(MarketPackets.MAX_PRICE_TEXT);
            price.setBordered(false);
            price.setHint(Component.translatable("markettrade.ui.price_hint"));
            price.setResponder(text -> priceChanged());
            amount = new EditBox(f, 0, 0, 80, 14, Component.translatable("markettrade.ui.amount_hint"));
            amount.setMaxLength(MarketPackets.MAX_PRICE_TEXT);
            amount.setBordered(false);
            amount.setHint(Component.translatable("markettrade.ui.amount_hint"));
            reason = new EditBox(f, 0, 0, 80, 14, Component.translatable("markettrade.ui.report_hint"));
            reason.setMaxLength(MarketPackets.MAX_REASON);
            reason.setBordered(false);
            reason.setHint(Component.translatable("markettrade.ui.report_hint"));
        }
        for (EditBox box : List.of(search, price, amount, reason)) {
            box.setVisible(false);
            addWidget(box);
        }
        Categories.ensure();
        // Screen.init runs twice when the screen opens, so only ask again if something differs.
        String key = width + "x" + height + view;
        if (!key.equals(requestedFor)) {
            requestedFor = key;
            refresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientData.preview = null;
    }

    /** Asks for the data of whatever is on screen. */
    private void refresh() {
        ClientData.requestStatus();
        switch (view) {
            case BROWSE -> ClientData.requestSummary();
            case ITEM -> ClientData.requestItem(itemId, itemPage, lay.listingRows());
            case MINE -> ClientData.requestMine(minePage, lay.rows());
            case RETURNS -> ClientData.requestReturns(returnsPage, lay.rows());
            case WALLET -> ClientData.requestWallet();
            case SELL -> {
            }
        }
    }

    /** Called after the server answers an action: show the new state. */
    void afterReply() {
        if (view == View.SELL && ClientData.replyOk) {
            selectedSlot = -1;
            selected = ItemStack.EMPTY;
            price.setValue("");
            ClientData.preview = null;
        }
        refresh();
    }

    void dataChanged(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (payload instanceof MarketPackets.ItemPagePayload p) {
            itemPage = p.page().page();
        } else if (payload instanceof MarketPackets.MyListingsPayload p) {
            minePage = p.data().page();
        } else if (payload instanceof MarketPackets.ReturnsPayload p) {
            returnsPage = p.data().page();
        }
    }

    private void priceChanged() {
        previewDirty = true;
        previewDueAt = System.currentTimeMillis() + PREVIEW_DELAY_MS;
    }

    private void setView(View next) {
        view = next;
        overlay = Overlay.NONE;
        if (next != View.ITEM) {
            ClientData.item = null;
        }
        refresh();
    }

    // Names and icons

    private ItemStack defaultStack(String id) {
        return defaults.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            if (location != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
            return new ItemStack(Items.BARRIER);
        });
    }

    private boolean isKnown(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location);
    }

    private String plainName(String id) {
        return isKnown(id) ? defaultStack(id).getHoverName().getString() : id;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("markettrade.ui." + key, args).getString();
    }

    // Drawing helpers

    private Theme theme() {
        return ClientData.theme;
    }

    private int c(Token token) {
        return theme().c(token);
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    private void textRight(GuiGraphics g, String s, int right, int y, int color) {
        g.drawString(font, s, right - font.width(s), y, color, false);
    }

    /** Text centred on a line of the given height. */
    private int midY(int y, int h) {
        return y + (h - 8) / 2;
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, fill);
        if (theme().panel() == PanelStyle.BEVEL) {
            int light = c(Token.BEVEL_LIGHT);
            int dark = c(Token.BEVEL_DARK);
            g.fill(x, y, x + w, y + 1, light);
            g.fill(x, y, x + 1, y + h, light);
            g.fill(x, y + h - 1, x + w, y + h, dark);
            g.fill(x + w - 1, y, x + w, y + h, dark);
        } else {
            outline(g, x, y, w, h, edge);
        }
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void addHit(int x, int y, int w, int h, Runnable action) {
        if (action != null) {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, Kind kind, Runnable action) {
        boolean hover = action != null && over(mx, my, x, y, w, h);
        int bg;
        int fg;
        int edge;
        switch (kind) {
            case GOOD -> {
                bg = c(Token.BUY_BG);
                fg = c(Token.BUY_TEXT);
                edge = c(Token.BUY_EDGE);
            }
            case SHORT -> {
                bg = c(Token.SHORT_BG);
                fg = c(Token.SHORT_TEXT);
                edge = c(Token.SHORT_EDGE);
            }
            case TAB_ON -> {
                bg = c(Token.TAB_ON_BG);
                fg = c(Token.TAB_ON_TEXT);
                edge = c(Token.TAB_ON_EDGE);
            }
            case TAB_OFF -> {
                bg = c(Token.TAB_OFF_BG);
                fg = c(Token.TAB_OFF_TEXT);
                edge = c(Token.TAB_OFF_EDGE);
            }
            default -> {
                bg = c(Token.BUTTON_BG);
                fg = c(Token.BUTTON_TEXT);
                edge = c(Token.BUTTON_EDGE);
            }
        }
        if (hover && kind == Kind.NORMAL) {
            bg = c(Token.BUTTON_HOVER);
        }
        if (theme().panel() == PanelStyle.BEVEL) {
            int light = kind == Kind.GOOD ? edge : kind == Kind.SHORT ? c(Token.SHORT_EDGE) : c(Token.BEVEL_LIGHT);
            g.fill(x, y, x + w, y + h, bg);
            g.fill(x, y, x + w, y + 1, light);
            g.fill(x, y, x + 1, y + h, light);
            int dark = c(Token.BEVEL_DARK);
            g.fill(x, y + h - 1, x + w, y + h, dark);
            g.fill(x + w - 1, y, x + w, y + h, dark);
        } else {
            g.fill(x, y, x + w, y + h, bg);
            outline(g, x, y, w, h, edge);
        }
        String shown = trim(label, w - 6);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, midY(y, h), fg, false);
        addHit(x, y, w, h, action);
    }

    private void icon(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        if (size == 16) {
            g.renderItem(stack, x, y);
            return;
        }
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** An item icon, in a slot box for themes that draw them. Returns the width used. */
    private int iconWithSlot(GuiGraphics g, ItemStack stack, int x, int y, int rowH) {
        int box = Math.min(rowH, 20);
        int by = y + (rowH - box) / 2;
        if (theme().iconSlots()) {
            int dark = c(Token.BEVEL_DARK);
            int light = c(Token.BEVEL_LIGHT);
            g.fill(x, by, x + box, by + box, c(Token.BUTTON_BG));
            g.fill(x, by, x + box, by + 1, dark);
            g.fill(x, by, x + 1, by + box, dark);
            g.fill(x, by + box - 1, x + box, by + box, light);
            g.fill(x + box - 1, by, x + box, by + box, light);
        }
        icon(g, stack, x + (box - 16) / 2, by + (box - 16) / 2, 16);
        return box;
    }

    private void rowBackground(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        if (theme().rows() == RowStyle.LEDGER) {
            if (hover) {
                g.fill(x, y, x + w, y + h, c(Token.ROW_HOVER));
            }
            g.fill(x, y + h - 1, x + w, y + h, c(Token.ROW_LINE));
        } else {
            g.fill(x, y, x + w, y + h, hover ? c(Token.ROW_HOVER) : c(Token.ROW_BG));
        }
    }

    // Frame

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        tooltip = ItemStack.EMPTY;
        Theme th = theme();
        g.fill(0, 0, width, height, c(Token.BG));
        tickPreview();
        updateBoxes();

        // The main surface of the page.
        int mainX = view == View.BROWSE && !lay.collapsed() ? lay.sideW() : 0;
        int mainY = lay.headerH();
        int mainW = width - mainX;
        int mainH = height - mainY;
        if (th.panel() == PanelStyle.BEVEL || th.rows() == RowStyle.LEDGER) {
            int inset = 3;
            panel(g, mainX + inset, mainY + inset, mainW - 2 * inset, mainH - 2 * inset, c(Token.MAIN_BG), c(Token.MAIN_LINE));
        } else {
            g.fill(mainX, mainY, mainX + mainW, mainY + mainH, c(Token.MAIN_BG));
        }

        switch (view) {
            case BROWSE -> drawBrowse(g, mx, my);
            case ITEM -> drawItem(g, mx, my);
            case MINE -> drawMine(g, mx, my);
            case RETURNS -> drawReturns(g, mx, my);
            case WALLET -> drawWallet(g, mx, my);
            case SELL -> drawSell(g, mx, my);
        }
        drawHeader(g, mx, my);

        if (overlay != Overlay.NONE) {
            hits.clear();
            tooltip = ItemStack.EMPTY;
            // Item icons draw at a raised depth, so anything meant to cover them has to sit above that.
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(0, 0, width, height, c(Token.SHADE));
            switch (overlay) {
                case BUY -> drawBuy(g, mx, my);
                case REPORT -> drawReport(g, mx, my);
                case THEME -> drawThemePicker(g, mx, my);
                case CATEGORIES -> drawCategoryMenu(g, mx, my);
                default -> {
                }
            }
            g.pose().popPose();
        }
        if (!tooltip.isEmpty()) {
            g.renderTooltip(font, tooltip, mx, my);
        }
    }

    private void updateBoxes() {
        search.setVisible(false);
        price.setVisible(false);
        amount.setVisible(false);
        reason.setVisible(false);
        int textColor = c(Token.INPUT_TEXT);
        for (EditBox box : List.of(search, price, amount, reason)) {
            box.setTextColor(textColor);
        }
    }

    private void tickPreview() {
        if (view == View.SELL && previewDirty && System.currentTimeMillis() >= previewDueAt) {
            previewDirty = false;
            if (selectedSlot >= 0 && !selected.isEmpty()) {
                ClientData.send(new MarketPackets.SellRequest(selectedSlot, selected.getCount(),
                        BuiltInRegistries.ITEM.getId(selected.getItem()), price.getValue(), false));
            }
        }
    }

    private void drawInput(GuiGraphics g, int mx, int my, EditBox box, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, c(Token.INPUT_BG));
        outline(g, x, y, w, h, c(Token.INPUT_EDGE));
        box.setX(x + 4);
        box.setY(y + (h - 8) / 2);
        box.setWidth(w - 8);
        box.setHeight(10);
        box.setVisible(true);
        box.render(g, mx, my, 0);
    }

    // Header

    private void drawHeader(GuiGraphics g, int mx, int my) {
        int h = lay.headerH();
        g.fill(0, 0, width, h, c(Token.HEADER_BG));
        g.fill(0, h - 1, width, h, c(Token.HEADER_LINE));

        MarketViews.Status status = ClientData.status;
        boolean onItem = view == View.ITEM;
        String balance = status == null ? "" : status.balanceText();

        String tabBrowse = t("tab_browse");
        String tabMine = t("tab_mine") + (status != null && status.myListings() > 0 ? " (" + status.myListings() + ")" : "");
        String tabReturns = t("tab_returns") + (status != null && status.returns() > 0 ? " (" + status.returns() + ")" : "");
        String tabWallet = t("tab_wallet");
        String themeLabel = t("theme");
        String sellFull = t("sell");
        String sellShort = t("sell_short");
        String back = t("back");

        int gap = 4;
        int padX = 8;
        int btnH = 16;
        int y = (h - btnH) / 2 - 1;
        int tabsW = 0;
        String[] tabs = { tabBrowse, tabMine, tabReturns, tabWallet };
        for (String tab : tabs) {
            tabsW += font.width(tab) + 2 * padX + gap;
        }
        String title = t("title");
        int titleW = font.width(title) + 12;
        int balW = balance.isEmpty() ? 0 : font.width(balance) + 10;

        boolean showBalance = true;
        String sell = sellFull;
        int rightW = balW + font.width(themeLabel) + 2 * padX + gap + font.width(sell) + 2 * padX + gap;
        int free = width - 6;
        boolean showTitle = tabsW + rightW + titleW <= free;
        if (tabsW + rightW > free) {
            sell = sellShort;
            rightW = balW + font.width(themeLabel) + 2 * padX + gap + font.width(sell) + 2 * padX + gap;
        }
        if (tabsW + rightW > free) {
            showBalance = false;
            rightW -= balW;
        }

        int x = 4;
        if (onItem) {
            int bw = font.width(back) + 2 * padX;
            button(g, mx, my, x, y, bw, btnH, back, Kind.NORMAL, () -> setView(View.BROWSE));
            x += bw + gap + 2;
        } else if (showTitle) {
            text(g, title, x + 2, midY(0, h - 1), c(Token.TITLE));
            x += titleW;
        }
        View[] views = { View.BROWSE, View.MINE, View.RETURNS, View.WALLET };
        for (int i = 0; i < 4; i++) {
            int bw = font.width(tabs[i]) + 2 * padX;
            boolean on = view == views[i] || (views[i] == View.BROWSE && view == View.ITEM);
            View go = views[i];
            button(g, mx, my, x, y, bw, btnH, tabs[i], on ? Kind.TAB_ON : Kind.TAB_OFF, () -> {
                if (view != go || go == View.BROWSE && view == View.ITEM) {
                    setView(go);
                }
            });
            x += bw + gap;
        }

        int right = width - 4;
        int sellW = font.width(sell) + 2 * padX;
        button(g, mx, my, right - sellW, y, sellW, btnH, sell, Kind.GOOD, () -> {
            selectedSlot = -1;
            selected = ItemStack.EMPTY;
            price.setValue("");
            ClientData.preview = null;
            setView(View.SELL);
        });
        right -= sellW + gap;
        int themeW = font.width(themeLabel) + 2 * padX;
        button(g, mx, my, right - themeW, y, themeW, btnH, themeLabel, Kind.NORMAL, () -> overlay = Overlay.THEME);
        right -= themeW + gap + 2;
        if (showBalance && !balance.isEmpty()) {
            textRight(g, balance, right, midY(0, h - 1), c(Token.HEADER_PRICE));
        }
    }

    // Footer

    private void drawFooter(GuiGraphics g, int mx, int my, int left, int right, int from, int to, int total, int page, int pages,
            Runnable prev, Runnable next) {
        int y = lay.footerTop();
        int h = lay.footerH();
        int by = y + (h - 14) / 2;
        int nextW = font.width(t("next")) + 12;
        int prevW = font.width(t("previous")) + 12;
        String pageText = t("page", page + 1, pages);
        int pagerW = prevW + nextW + font.width(pageText) + 16;
        boolean reply = !ClientData.replyLines.isEmpty() && System.currentTimeMillis() - ClientData.replyAt < REPLY_SHOW_MS;
        String info;
        int color;
        if (reply) {
            StringBuilder joined = new StringBuilder();
            for (Component line : ClientData.replyLines) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(line.getString());
            }
            info = joined.toString();
            color = ClientData.replyOk ? c(Token.NOTE_OK) : c(Token.NOTE_BAD);
        } else if (total > 0) {
            info = t("showing", from, to, total);
            color = c(Token.TEXT_DIM);
        } else {
            info = "";
            color = c(Token.TEXT_DIM);
        }
        boolean pager = pages > 1 && prev != null;
        int avail = right - left - (pager ? pagerW + 8 : 0);
        text(g, trim(info, avail), left, midY(y, h), color);
        if (pager) {
            int x = right - pagerW;
            button(g, mx, my, x, by, prevW, 14, t("previous"), Kind.NORMAL, page > 0 ? prev : null);
            x += prevW + 8;
            text(g, pageText, x, midY(y, h), c(Token.TEXT));
            x += font.width(pageText) + 8;
            button(g, mx, my, x, by, nextW, 14, t("next"), Kind.NORMAL, page < pages - 1 ? next : null);
        }
    }

    // Browse

    private List<SummaryRow> filteredRows() {
        String key = search.getValue() + "|" + category + "|" + sort + "|" + ClientData.SUMMARY.size()
                + "|" + System.identityHashCode(ClientData.SUMMARY.isEmpty() ? null : ClientData.SUMMARY.get(0));
        if (!key.equals(shownKey) || shownRows.isEmpty() != ClientData.SUMMARY.isEmpty()) {
            shownKey = key;
            List<SummaryRow> matching = new ArrayList<>();
            List<Categories.Category> tabs = Categories.tabs();
            for (SummaryRow row : ClientData.SUMMARY) {
                if (!ListingSort.matches(plainName(row.itemId()), row.itemId(), search.getValue())) {
                    continue;
                }
                if (category >= 0 && category < tabs.size() && !tabs.get(category).itemIds().contains(row.itemId())) {
                    continue;
                }
                if (category == OTHER && inAnyTab(row.itemId())) {
                    continue;
                }
                matching.add(row);
            }
            shownRows = SummaryRows.sorted(matching, sort, this::plainName);
        }
        return shownRows;
    }

    private boolean inAnyTab(String id) {
        for (Categories.Category tab : Categories.tabs()) {
            if (tab.itemIds().contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Categories that hold at least one listed item, as indexes into the tab list, plus ALL first and OTHER last. */
    private List<Integer> visibleCategories() {
        List<Integer> out = new ArrayList<>();
        out.add(ALL);
        List<Categories.Category> tabs = Categories.tabs();
        boolean other = false;
        boolean[] used = new boolean[tabs.size()];
        for (SummaryRow row : ClientData.SUMMARY) {
            boolean any = false;
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).itemIds().contains(row.itemId())) {
                    used[i] = true;
                    any = true;
                }
            }
            if (!any) {
                other = true;
            }
        }
        for (int i = 0; i < used.length; i++) {
            if (used[i]) {
                out.add(i);
            }
        }
        if (other) {
            out.add(OTHER);
        }
        return out;
    }

    private String categoryName(int index) {
        if (index == ALL) {
            return t("all_items");
        }
        if (index == OTHER) {
            return t("everything_else");
        }
        List<Categories.Category> tabs = Categories.tabs();
        return index >= 0 && index < tabs.size() ? tabs.get(index).name().getString() : t("all_items");
    }

    private void drawBrowse(GuiGraphics g, int mx, int my) {
        if (!lay.collapsed()) {
            int sx = 0;
            int sy = lay.headerH();
            int sw = lay.sideW();
            g.fill(sx, sy, sx + sw, height, c(Token.SIDE_BG));
            g.fill(sx + sw - 1, sy, sx + sw, height, c(Token.SIDE_LINE));
            text(g, t("search"), sx + 6, sy + 6, c(Token.SIDE_DIM));
            drawInput(g, mx, my, search, sx + 6, sy + 17, sw - 12, 16);
            text(g, t("category"), sx + 6, sy + 40, c(Token.SIDE_DIM));
            int cy = sy + 52;
            int step = 16;
            int maxY = height - 4;
            List<Integer> cats = visibleCategories();
            sideFit = Math.max(1, (maxY - 14 - cy) / step + 1);
            sideTotal = cats.size();
            sideScroll = Math.max(0, Math.min(sideScroll, sideTotal - sideFit));
            for (int n = sideScroll; n < cats.size(); n++) {
                int index = cats.get(n);
                if (cy + 14 > maxY) {
                    break;
                }
                boolean sel = index == category;
                int idx = index;
                if (sel) {
                    g.fill(sx + 3, cy, sx + sw - 4, cy + 14, c(Token.SIDE_SEL_BG));
                } else if (over(mx, my, sx + 3, cy, sw - 7, 14)) {
                    g.fill(sx + 3, cy, sx + sw - 4, cy + 14, c(Token.ROW_HOVER));
                }
                text(g, trim(categoryName(index), sw - 18), sx + 8, midY(cy, 14),
                        sel ? c(Token.SIDE_SEL_TEXT) : c(Token.SIDE_TEXT));
                addHit(sx + 3, cy, sw - 7, 14, () -> {
                    category = idx;
                    browsePage = 0;
                });
                cy += step;
            }
            if (sideTotal > sideFit) {
                int trackTop = sy + 52;
                int trackH = sideFit * step - 2;
                int barH = Math.max(8, trackH * sideFit / sideTotal);
                int barY = trackTop + (trackH - barH) * sideScroll / (sideTotal - sideFit);
                g.fill(sx + sw - 3, trackTop, sx + sw - 1, trackTop + trackH, c(Token.SIDE_LINE));
                g.fill(sx + sw - 3, barY, sx + sw - 1, barY + barH, c(Token.SIDE_DIM));
            }
        } else {
            int y = lay.headerH() + lay.pad() - 2;
            int sx = lay.pad();
            int catW = Math.min(110, width / 3);
            drawInput(g, mx, my, search, sx, y, width - 2 * sx - catW - 4, 16);
            String catLabel = trim(categoryName(category), catW - 8);
            button(g, mx, my, width - sx - catW, y, catW, 16, catLabel, Kind.NORMAL, () -> {
                menuScroll = 0;
                overlay = Overlay.CATEGORIES;
            });
        }

        List<SummaryRow> rows = filteredRows();
        int left = lay.listLeft();
        int right = lay.listRight();
        int top = lay.listTop();
        int headY = top - lay.colHeadH();
        int rowsPerPage = lay.rows();
        int pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        browsePage = Math.max(0, Math.min(browsePage, pages - 1));

        int priceColW = Math.min(110, (right - left) / 3);
        int listedW = 44;
        text(g, t("col_item"), left + 4, headY + 2, c(Token.TEXT_DIM));
        String sortLabel = t("sort_label", t("sort_" + sort.key()));
        int sortW = font.width(sortLabel) + 8;
        button(g, mx, my, right - sortW, headY - 2, sortW, 12, sortLabel, Kind.NORMAL, () -> {
            sort = sort.next();
            browsePage = 0;
        });
        textRight(g, t("col_listed"), right - priceColW - 10, headY + 2, c(Token.TEXT_DIM));

        if (!ClientData.summaryReady && ClientData.SUMMARY.isEmpty()) {
            text(g, t("loading"), left + 4, top + 6, c(Token.TEXT_DIM));
        } else if (rows.isEmpty()) {
            text(g, ClientData.SUMMARY.isEmpty() ? t("empty") : t("no_results"), left + 4, top + 6, c(Token.TEXT_DIM));
        }
        int start = browsePage * rowsPerPage;
        int end = Math.min(rows.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            SummaryRow row = rows.get(i);
            int y = top + (i - start) * lay.rowStep();
            int rowW = right - left;
            boolean hover = overlay == Overlay.NONE && over(mx, my, left, y, rowW, lay.rowH());
            rowBackground(g, left, y, rowW, lay.rowH(), hover);
            ItemStack stack = defaultStack(row.itemId());
            int boxW = iconWithSlot(g, stack, left + 3, y, lay.rowH());
            int nameX = left + 3 + boxW + 6;
            String name = row.available() ? plainName(row.itemId()) : row.itemId();
            int nameMax = right - priceColW - listedW - 14 - nameX;
            text(g, trim(name, nameMax), nameX, midY(y, lay.rowH()), row.available() ? c(Token.TEXT) : c(Token.TEXT_DIM));
            textRight(g, String.valueOf(row.listed()), right - priceColW - 10, midY(y, lay.rowH()), c(Token.TEXT));
            textRight(g, trim(row.lowestText(), priceColW), right - 6, midY(y, lay.rowH()), c(Token.PRICE));
            String id = row.itemId();
            addHit(left, y, rowW, lay.rowH(), () -> {
                itemId = id;
                itemPage = 0;
                ClientData.item = null;
                setView(View.ITEM);
            });
        }
        drawFooter(g, mx, my, left, right, rows.isEmpty() ? 0 : start + 1, end, rows.size(), browsePage, pages,
                () -> browsePage--, () -> browsePage++);
    }

    // Item page

    private String detailsOf(MarketViews.ListingRow row, boolean[] special) {
        if (!row.available()) {
            special[0] = false;
            return t("unavailable");
        }
        if (row.stack().isEmpty()) {
            special[0] = false;
            return t("too_large");
        }
        ItemStack stack = row.stack();
        List<String> parts = new ArrayList<>();
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            parts.add(t("named", custom.getString()));
            special[0] = true;
        }
        for (var component : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            ItemEnchantments enchants = stack.get(component);
            if (enchants != null) {
                for (var entry : enchants.entrySet()) {
                    parts.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
                }
            }
        }
        if (parts.isEmpty()) {
            special[0] = false;
            return t("plain");
        }
        return String.join(", ", parts);
    }

    private void drawItem(GuiGraphics g, int mx, int my) {
        MarketViews.ItemPage page = ClientData.item;
        int left = lay.pad() + 3;
        int right = width - lay.pad() - 3;
        int top = lay.headerH() + lay.pad() + 2;
        int infoH = lay.infoH();
        ItemStack base = defaultStack(itemId);

        // Info block
        int infoW = Math.min(220, (right - left) * 2 / 5);
        g.fill(left, top, left + infoW, top + infoH, c(Token.ROW_BG));
        int iconBox = 36;
        if (theme().iconSlots()) {
            panel(g, left + 6, top + 6, iconBox, iconBox, c(Token.BUTTON_BG), c(Token.BUTTON_EDGE));
        }
        icon(g, base, left + 8, top + 8, 32);
        int tx = left + 8 + iconBox + 6;
        int tw = infoW - (tx - left) - 4;
        text(g, trim(plainName(itemId), tw), tx, top + 6, c(Token.TEXT));
        String cat = Categories.namesFor(itemId);
        int line = top + 18;
        if (!cat.isEmpty()) {
            text(g, trim(firstOf(cat), tw), tx, line, c(Token.TEXT_DIM));
            line += 11;
        }
        if (page != null) {
            text(g, trim(t("listed", page.listed()), tw), tx, line, c(Token.TEXT_DIM));
            line += 11;
            if (!page.lowestText().isEmpty()) {
                text(g, trim(t("from_price", page.lowestText()), tw), tx, line, c(Token.PRICE));
            }
        }

        // Chart
        int cx = left + infoW + 6;
        int cw = right - cx;
        g.fill(cx, top, cx + cw, top + infoH, c(Token.CHART_BG));
        if (theme().rows() == RowStyle.LEDGER || theme().iconSlots()) {
            outline(g, cx, top, cw, infoH, c(Token.CHART_AXIS));
        }
        text(g, t("recent_sales"), cx + 6, top + 4, c(Token.CHART_TEXT));
        if (page != null) {
            String note = page.averageText().isEmpty() ? t("chart_none") : t("chart_note", page.averageText());
            String shown = trim(note, cw - 20 - font.width(t("recent_sales")));
            textRight(g, shown, cx + cw - 6, top + 4, c(Token.CHART_TEXT));
            long max = 0;
            for (long v : page.chart()) {
                max = Math.max(max, v);
            }
            int areaX = cx + 6;
            int areaW = cw - 12;
            int areaBottom = top + infoH - 5;
            int areaTop = top + 16;
            int n = SalesChart.DAYS;
            int barGap = 2;
            int barW = Math.max(2, (areaW - barGap * (n - 1)) / n);
            int used = barW * n + barGap * (n - 1);
            int bx = areaX + (areaW - used) / 2;
            g.fill(areaX, areaBottom, areaX + areaW, areaBottom + 1, c(Token.CHART_AXIS));
            for (int i = 0; i < n; i++) {
                long v = page.chart()[i];
                if (v >= 0 && max > 0) {
                    int bh = (int) Math.max(2, (areaBottom - areaTop) * v / max);
                    g.fill(bx + i * (barW + barGap), areaBottom - bh, bx + i * (barW + barGap) + barW, areaBottom,
                            c(Token.CHART_BAR));
                }
            }
        }

        // Listings
        int headY = top + infoH + 4;
        int colCount = left + 4;
        int btnW = Math.max(30, font.width(t("report")) + 10);
        int buyW = Math.max(34, Math.max(font.width(t("buy")), Math.max(font.width(t("short")), font.width(t("yours")))) + 10);
        int priceW = 96;
        int timeW = 40;
        int xBuy = right - buyW;
        int xReport = xBuy - 4 - btnW;
        int xPrice = xReport - 6 - priceW;
        int xTime = xPrice - 6 - timeW;
        int xSeller = colCount + 26;
        int sellerW = xTime - 6 - xSeller;
        text(g, t("col_count"), colCount, headY, c(Token.TEXT_DIM));
        text(g, t("col_seller"), xSeller, headY, c(Token.TEXT_DIM));
        text(g, t("col_time"), xTime, headY, c(Token.TEXT_DIM));
        textRight(g, t("col_price"), xPrice + priceW, headY, c(Token.TEXT_DIM));

        int y = headY + lay.colHeadH();
        if (page == null) {
            text(g, t("loading"), colCount, y + 4, c(Token.TEXT_DIM));
        } else if (page.rows().isEmpty()) {
            text(g, t("empty"), colCount, y + 4, c(Token.TEXT_DIM));
        }
        long spendable = ClientData.status == null ? 0 : ClientData.status.spendable();
        if (page != null) {
            for (MarketViews.ListingRow row : page.rows()) {
                int rh = lay.listingH() - 2;
                boolean hover = overlay == Overlay.NONE && over(mx, my, left, y, right - left, rh);
                rowBackground(g, left, y, right - left, rh, hover);
                int ty = y + (rh - 17) / 2;
                text(g, String.valueOf(row.count()), colCount, midY(y, rh), c(Token.TEXT));
                boolean[] special = { false };
                String details = detailsOf(row, special);
                text(g, trim(row.seller(), sellerW), xSeller, ty, c(Token.TEXT));
                text(g, trim(details, sellerW), xSeller, ty + 9, special[0] ? c(Token.SPECIAL) : c(Token.TEXT_DIM));
                text(g, TimeLeft.format(row.msLeft()), xTime, midY(y, rh), c(Token.TEXT));
                textRight(g, trim(row.priceText(), priceW), xPrice + priceW, row.totalText().isEmpty() ? midY(y, rh) : ty,
                        c(Token.PRICE));
                if (!row.totalText().isEmpty()) {
                    textRight(g, trim("(" + row.totalText() + ")", priceW), xPrice + priceW, ty + 9, c(Token.TEXT_DIM));
                }
                if (hover && over(mx, my, left, y, xReport - 4 - left, rh) && !row.stack().isEmpty()) {
                    tooltip = row.stack();
                }
                int by = y + (rh - 16) / 2;
                button(g, mx, my, xReport, by, btnW, 16, t("report"), Kind.NORMAL, row.yours() ? null : () -> {
                    target = row;
                    reason.setValue("");
                    overlay = Overlay.REPORT;
                    setFocused(reason);
                    reason.setFocused(true);
                });
                if (!row.available()) {
                    // No Buy for a listing whose item can't be loaded.
                } else if (row.yours()) {
                    button(g, mx, my, xBuy, by, buyW, 16, t("yours"), Kind.SHORT, null);
                } else if (spendable < row.price()) {
                    button(g, mx, my, xBuy, by, buyW, 16, t("short"), Kind.SHORT, null);
                } else {
                    button(g, mx, my, xBuy, by, buyW, 16, t("buy"), Kind.GOOD, () -> askBuy(row));
                }
                y += lay.listingH();
            }
        }
        int from = page == null || page.rows().isEmpty() ? 0 : page.page() * lay.listingRows() + 1;
        int to = page == null ? 0 : from - 1 + page.rows().size();
        drawFooter(g, mx, my, left, right, from, to, page == null ? 0 : page.listed(), page == null ? 0 : page.page(),
                page == null ? 1 : page.pages(), () -> {
                    itemPage--;
                    ClientData.requestItem(itemId, itemPage, lay.listingRows());
                }, () -> {
                    itemPage++;
                    ClientData.requestItem(itemId, itemPage, lay.listingRows());
                });
    }

    private static String firstOf(String names) {
        int comma = names.indexOf(", ");
        return comma < 0 ? names : names.substring(0, comma);
    }

    private void askBuy(MarketViews.ListingRow row) {
        target = row;
        overlay = Overlay.BUY;
    }

    // My listings

    private void drawMine(GuiGraphics g, int mx, int my) {
        MarketViews.MyListings data = ClientData.mine;
        int left = lay.pad() + 3;
        int right = width - lay.pad() - 3;
        int top = lay.listTop();
        int headY = top - lay.colHeadH();
        int cancelW = font.width(t("cancel_listing")) + 14;
        int priceW = 110;
        int timeW = 44;
        int xCancel = right - cancelW;
        int xPrice = xCancel - 8 - priceW;
        int xTime = xPrice - 8 - timeW;
        text(g, t("col_item"), left + 4, headY + 2, c(Token.TEXT_DIM));
        text(g, t("col_time"), xTime, headY + 2, c(Token.TEXT_DIM));
        textRight(g, t("col_price"), xPrice + priceW, headY + 2, c(Token.TEXT_DIM));
        if (data == null) {
            text(g, t("loading"), left + 4, top + 6, c(Token.TEXT_DIM));
        } else if (data.rows().isEmpty()) {
            text(g, t("empty"), left + 4, top + 6, c(Token.TEXT_DIM));
        } else {
            int y = top;
            for (MarketViews.ListingRow row : data.rows()) {
                boolean hover = overlay == Overlay.NONE && over(mx, my, left, y, right - left, lay.rowH());
                rowBackground(g, left, y, right - left, lay.rowH(), hover);
                ItemStack stack = row.stack().isEmpty() ? defaultStack(row.itemId()) : row.stack();
                int boxW = iconWithSlot(g, stack, left + 3, y, lay.rowH());
                int nameX = left + 3 + boxW + 6;
                String name = !row.available() ? row.itemId() : stack.getHoverName().getString();
                if (row.count() > 1) {
                    name = name + " x" + row.count();
                }
                text(g, trim(name, xTime - 6 - nameX), nameX, midY(y, lay.rowH()), row.available() ? c(Token.TEXT) : c(Token.TEXT_DIM));
                text(g, TimeLeft.format(row.msLeft()), xTime, midY(y, lay.rowH()), c(Token.TEXT));
                boolean two = !row.totalText().isEmpty();
                textRight(g, trim(row.priceText(), priceW), xPrice + priceW, two ? y + 3 : midY(y, lay.rowH()), c(Token.PRICE));
                if (two) {
                    textRight(g, trim("(" + row.totalText() + ")", priceW), xPrice + priceW, y + 12, c(Token.TEXT_DIM));
                }
                if (hover && over(mx, my, left, y, xTime - left, lay.rowH()) && !row.stack().isEmpty()) {
                    tooltip = row.stack();
                }
                long id = row.id();
                button(g, mx, my, xCancel, y + (lay.rowH() - 16) / 2, cancelW, 16, t("cancel_listing"), Kind.NORMAL,
                        () -> ClientData.send(new MarketPackets.CancelRequest(id)));
                y += lay.rowStep();
            }
        }
        int per = lay.rows();
        int from = data == null || data.rows().isEmpty() ? 0 : data.page() * per + 1;
        int to = data == null ? 0 : from - 1 + data.rows().size();
        drawFooter(g, mx, my, left, right, from, to, data == null ? 0 : data.total(), data == null ? 0 : data.page(),
                data == null ? 1 : data.pages(), () -> {
                    minePage--;
                    ClientData.requestMine(minePage, per);
                }, () -> {
                    minePage++;
                    ClientData.requestMine(minePage, per);
                });
    }

    // Returns

    private void drawReturns(GuiGraphics g, int mx, int my) {
        MarketViews.Returns data = ClientData.returns;
        int left = lay.pad() + 3;
        int right = width - lay.pad() - 3;
        int top = lay.listTop();
        int headY = top - lay.colHeadH();
        int claimW = font.width(t("claim")) + 14;
        int claimAllW = font.width(t("claim_all")) + 14;
        text(g, t("col_item"), left + 4, headY + 2, c(Token.TEXT_DIM));
        button(g, mx, my, right - claimAllW, headY - 2, claimAllW, 12, t("claim_all"), Kind.GOOD,
                data == null || data.total() == 0 ? null : () -> ClientData.send(new MarketPackets.ClaimRequest(-1, 0, 0)));
        if (data == null) {
            text(g, t("loading"), left + 4, top + 6, c(Token.TEXT_DIM));
        } else if (data.rows().isEmpty()) {
            text(g, t("empty"), left + 4, top + 6, c(Token.TEXT_DIM));
        } else {
            int y = top;
            for (MarketViews.ReturnRow row : data.rows()) {
                boolean hover = overlay == Overlay.NONE && over(mx, my, left, y, right - left, lay.rowH());
                rowBackground(g, left, y, right - left, lay.rowH(), hover);
                ItemStack stack = row.stack().isEmpty() ? defaultStack(row.itemId()) : row.stack();
                int boxW = iconWithSlot(g, stack, left + 3, y, lay.rowH());
                int nameX = left + 3 + boxW + 6;
                String name = !row.available() ? row.itemId() : stack.getHoverName().getString();
                name = name + " x" + row.count();
                text(g, trim(name, right - claimW - 10 - nameX), nameX, midY(y, lay.rowH()),
                        row.available() ? c(Token.TEXT) : c(Token.TEXT_DIM));
                if (hover && over(mx, my, left, y, right - claimW - 8 - left, lay.rowH()) && !row.stack().isEmpty()) {
                    tooltip = row.stack();
                }
                int index = row.index();
                int count = row.count();
                int token = row.token();
                button(g, mx, my, right - claimW, y + (lay.rowH() - 16) / 2, claimW, 16, t("claim"),
                        row.available() ? Kind.NORMAL : Kind.SHORT,
                        row.available() ? () -> ClientData.send(new MarketPackets.ClaimRequest(index, count, token)) : null);
                y += lay.rowStep();
            }
        }
        int per = lay.rows();
        int from = data == null || data.rows().isEmpty() ? 0 : data.page() * per + 1;
        int to = data == null ? 0 : from - 1 + data.rows().size();
        drawFooter(g, mx, my, left, right, from, to, data == null ? 0 : data.total(), data == null ? 0 : data.page(),
                data == null ? 1 : data.pages(), () -> {
                    returnsPage--;
                    ClientData.requestReturns(returnsPage, per);
                }, () -> {
                    returnsPage++;
                    ClientData.requestReturns(returnsPage, per);
                });
    }

    // Wallet

    private void drawWallet(GuiGraphics g, int mx, int my) {
        MarketViews.WalletView data = ClientData.wallet;
        int left = lay.pad() + 8;
        int right = width - lay.pad() - 8;
        int y = lay.headerH() + lay.pad() + 6;
        String balance = data == null ? "" : data.balanceText();
        text(g, t("balance", balance), left, y, c(Token.PRICE));
        y += 16;
        if (data != null && !data.hasItems()) {
            text(g, trim(Component.translatable("markettrade.currency_unavailable").getString(), right - left), left, y,
                    c(Token.TEXT_DIM));
        }
        int btnW = font.width(t("deposit_all")) + 16;
        button(g, mx, my, left, y, btnW, 16, t("deposit_all"), Kind.GOOD,
                data != null && data.hasItems() ? () -> ClientData.send(new MarketPackets.WalletRequest(
                        MarketPackets.WalletKind.DEPOSIT_ALL.ordinal(), "", 0, "")) : null);
        y += 26;
        if (data != null && data.hasItems()) {
            text(g, t("withdraw"), left, y, c(Token.TEXT));
            text(g, trim(t("withdraw_hint"), right - left - font.width(t("withdraw")) - 8),
                    left + font.width(t("withdraw")) + 8, y, c(Token.TEXT_DIM));
            y += 14;
            int rowH = 22;
            int perRowW = Math.min(150, (right - left - 6) / 2);
            int col = 0;
            int x0 = left;
            for (MarketViews.CurrencyButton cb : data.buttons()) {
                int x = x0 + col * (perRowW + 6);
                if (y + rowH > height - lay.footerH() - 40) {
                    break;
                }
                boolean hover = overlay == Overlay.NONE && over(mx, my, x, y, perRowW, rowH);
                rowBackground(g, x, y, perRowW, rowH, hover);
                ItemStack stack = defaultStack(cb.itemId());
                int boxW = iconWithSlot(g, stack, x + 3, y, rowH);
                text(g, trim(cb.name(), perRowW - boxW - 12), x + 3 + boxW + 4, midY(y, rowH), c(Token.TEXT));
                String id = cb.itemId();
                addHit(x, y, perRowW, rowH, () -> {
                    if (hasShiftDown()) {
                        ClientData.send(new MarketPackets.WalletRequest(MarketPackets.WalletKind.WITHDRAW_STACK.ordinal(), id,
                                0, ""));
                    } else {
                        ClientData.send(new MarketPackets.WalletRequest(MarketPackets.WalletKind.WITHDRAW_ITEM.ordinal(), id,
                                1, ""));
                    }
                });
                col++;
                if (col >= 2 || x0 + (col) * (perRowW + 6) + perRowW > right) {
                    col = 0;
                    y += rowH + 2;
                }
            }
            if (col != 0) {
                y += rowH + 2;
            }
            y += 8;
            int aw = Math.min(150, right - left - 100);
            drawInput(g, mx, my, amount, left, y, aw, 16);
            String wlabel = t("withdraw_amount");
            int ww = font.width(wlabel) + 16;
            button(g, mx, my, left + aw + 6, y, ww, 16, wlabel, Kind.NORMAL, () -> {
                ClientData.send(new MarketPackets.WalletRequest(MarketPackets.WalletKind.WITHDRAW_AMOUNT.ordinal(), "", 0,
                        amount.getValue()));
                amount.setValue("");
            });
        }
        drawFooter(g, mx, my, left, right, 0, 0, 0, 0, 1, null, null);
    }

    // Sell

    private void drawSell(GuiGraphics g, int mx, int my) {
        int left = lay.pad() + 8;
        int top = lay.headerH() + lay.pad() + 4;
        text(g, t("your_items"), left, top, c(Token.TEXT_DIM));
        int slot = 18;
        int gy = top + 12;
        net.minecraft.world.entity.player.Inventory inv = Minecraft.getInstance().player == null ? null
                : Minecraft.getInstance().player.getInventory();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row < 3 ? 9 + row * 9 + col : col;
                int x = left + col * slot;
                int y = gy + row * slot + (row == 3 ? 4 : 0);
                boolean sel = index == selectedSlot;
                g.fill(x, y, x + slot - 1, y + slot - 1, sel ? c(Token.SIDE_SEL_BG) : c(Token.ROW_BG));
                outline(g, x, y, slot - 1, slot - 1, sel ? c(Token.BUY_EDGE) : c(Token.ROW_LINE));
                ItemStack stack = inv == null ? ItemStack.EMPTY : inv.getItem(index);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x + 1, y + 1);
                    g.renderItemDecorations(font, stack, x + 1, y + 1);
                    if (overlay == Overlay.NONE && over(mx, my, x, y, slot - 1, slot - 1)) {
                        tooltip = stack;
                    }
                    int idx = index;
                    addHit(x, y, slot - 1, slot - 1, () -> {
                        selectedSlot = idx;
                        selected = stack.copy();
                        previewDirty = true;
                        previewDueAt = System.currentTimeMillis();
                        ClientData.preview = null;
                    });
                }
            }
        }
        int rx = left + 9 * slot + 12;
        int rw = width - lay.pad() - 8 - rx;
        boolean stacked = rw < 110;
        if (stacked) {
            rx = left;
            rw = width - 2 * left;
            top = gy + 4 * slot + 8;
        } else {
            top = gy - 12;
        }
        int y = top;
        if (selected.isEmpty()) {
            text(g, trim(t("sell_pick"), rw), rx, y, c(Token.TEXT_DIM));
        } else {
            String name = selected.getHoverName().getString() + (selected.getCount() > 1 ? " x" + selected.getCount() : "");
            text(g, trim(name, rw), rx, y, c(Token.TEXT));
            y += 12;
            drawInput(g, mx, my, price, rx, y, rw, 16);
            y += 22;
            MarketViews.SellPreview preview = ClientData.preview;
            if (preview != null && preview.status() == MarketViews.SellStatus.OK) {
                text(g, trim(t("sell_price", preview.priceText()), rw), rx, y, c(Token.PRICE));
                text(g, trim(t("sell_cut", preview.cutPercent(), preview.cutText()), rw), rx, y + 11, c(Token.TEXT_DIM));
                text(g, trim(t("sell_get", preview.receiveText()), rw), rx, y + 22, c(Token.TEXT));
            } else if (preview != null) {
                text(g, trim(previewProblem(preview), rw), rx, y, c(Token.NOTE_BAD));
            } else if (price.getValue().isEmpty()) {
                text(g, trim(t("price_missing"), rw), rx, y, c(Token.TEXT_DIM));
            }
            y += 36;
            boolean ready = preview != null && preview.status() == MarketViews.SellStatus.OK && !previewDirty
                    && preview.asked().equals(price.getValue());
            String label = t("list_it");
            int bw = font.width(label) + 20;
            int slotNow = selectedSlot;
            ItemStack shown = selected;
            button(g, mx, my, rx, y, bw, 16, label, ready ? Kind.GOOD : Kind.SHORT, ready ? () -> {
                ClientData.send(new MarketPackets.SellRequest(slotNow, shown.getCount(), preview.token(), preview.asked(), true));
            } : null);
        }
        drawFooter(g, mx, my, left, width - lay.pad() - 8, 0, 0, 0, 0, 1, null, null);
    }

    private String previewProblem(MarketViews.SellPreview preview) {
        return switch (preview.status()) {
            case EMPTY_TEXT -> t("price_missing");
            case BAD_AMOUNT -> Component.translatable("markettrade.amount_bad").getString();
            case TOO_BIG -> Component.translatable("markettrade.amount_too_big").getString();
            case LIMIT -> Component.translatable("markettrade.sell_limit", preview.limit()).getString();
            case BANNED -> Component.translatable("markettrade.banned").getString();
            case CHANGED -> Component.translatable("markettrade.sell_changed").getString();
            case EMPTY_SLOT -> Component.translatable("markettrade.sell_empty").getString();
            default -> "";
        };
    }

    // Overlays

    private int[] dialog(GuiGraphics g, int w, int h) {
        w = Math.min(w, width - 12);
        h = Math.min(h, height - 12);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h, c(Token.DIALOG_BG), c(Token.DIALOG_EDGE));
        return new int[] { x, y, w, h };
    }

    private void drawBuy(GuiGraphics g, int mx, int my) {
        if (target == null) {
            overlay = Overlay.NONE;
            return;
        }
        int[] d = dialog(g, 230, 96);
        int x = d[0] + 10;
        int y = d[1] + 8;
        int inner = d[2] - 20;
        text(g, t("confirm_buy"), x, y, c(Token.DIALOG_TEXT));
        y += 14;
        ItemStack stack = target.stack().isEmpty() ? defaultStack(target.itemId()) : target.stack();
        icon(g, stack, x, y - 1, 16);
        String name = stack.getHoverName().getString() + (target.count() > 1 ? " x" + target.count() : "");
        text(g, trim(name, inner - 22), x + 22, y + 4, c(Token.DIALOG_TEXT));
        y += 20;
        text(g, trim(target.priceText(), inner), x, y, c(Token.PRICE));
        y += 11;
        text(g, trim(t("seller_line", target.seller()), inner), x, y, c(Token.TEXT_DIM));
        MarketViews.ListingRow row = target;
        int bw = 70;
        int by = d[1] + d[3] - 24;
        button(g, mx, my, d[0] + d[2] - 10 - bw, by, bw, 16, t("confirm"), Kind.GOOD, () -> {
            ClientData.send(new MarketPackets.BuyRequest(row.id(), row.price()));
            overlay = Overlay.NONE;
        });
        button(g, mx, my, d[0] + d[2] - 10 - 2 * bw - 6, by, bw, 16, t("cancel"), Kind.NORMAL, () -> overlay = Overlay.NONE);
    }

    private void drawReport(GuiGraphics g, int mx, int my) {
        if (target == null) {
            overlay = Overlay.NONE;
            return;
        }
        int[] d = dialog(g, 230, 90);
        int x = d[0] + 10;
        int y = d[1] + 8;
        text(g, t("report_title"), x, y, c(Token.DIALOG_TEXT));
        y += 14;
        drawInput(g, mx, my, reason, x, y, d[2] - 20, 16);
        long id = target.id();
        int bw = 70;
        int by = d[1] + d[3] - 24;
        button(g, mx, my, d[0] + d[2] - 10 - bw, by, bw, 16, t("send"), Kind.GOOD, () -> {
            ClientData.send(new MarketPackets.ReportRequest(id, reason.getValue()));
            overlay = Overlay.NONE;
        });
        button(g, mx, my, d[0] + d[2] - 10 - 2 * bw - 6, by, bw, 16, t("cancel"), Kind.NORMAL, () -> overlay = Overlay.NONE);
    }

    private void drawThemePicker(GuiGraphics g, int mx, int my) {
        List<Theme> all = Themes.all();
        int[] d = dialog(g, 190, 36 + all.size() * 22);
        int x = d[0] + 10;
        int y = d[1] + 8;
        text(g, t("theme_title"), x, y, c(Token.DIALOG_TEXT));
        y += 16;
        for (Theme option : all) {
            boolean current = option.id().equals(theme().id());
            int rw = d[2] - 20;
            boolean hover = over(mx, my, x, y, rw, 18);
            g.fill(x, y, x + rw, y + 18, hover ? c(Token.ROW_HOVER) : c(Token.ROW_BG));
            if (current) {
                outline(g, x, y, rw, 18, c(Token.BUY_EDGE));
            }
            // Swatch: the background and the accent of that theme.
            g.fill(x + 3, y + 3, x + 15, y + 15, option.c(Token.BG));
            g.fill(x + 9, y + 3, x + 15, y + 15, option.c(Token.TITLE));
            g.fill(x + 3, y + 12, x + 15, y + 15, option.c(Token.MAIN_BG));
            outline(g, x + 3, y + 3, 12, 12, option.c(Token.SIDE_LINE));
            text(g, Component.translatable(option.nameKey()).getString(), x + 22, midY(y, 18), c(Token.TEXT));
            addHit(x, y, rw, 18, () -> {
                ClientData.setTheme(option);
                overlay = Overlay.NONE;
            });
            y += 22;
        }
    }

    private void drawCategoryMenu(GuiGraphics g, int mx, int my) {
        List<Integer> list = visibleCategories();
        int rows = Math.min(list.size(), Math.max(3, (height - 60) / 16));
        int[] d = dialog(g, Math.min(200, width - 20), 20 + rows * 16);
        int x = d[0] + 8;
        int y = d[1] + 8;
        menuFit = rows;
        menuTotal = list.size();
        menuScroll = Math.max(0, Math.min(menuScroll, menuTotal - menuFit));
        for (int i = menuScroll; i < menuScroll + rows; i++) {
            int index = list.get(i);
            int rw = d[2] - 16;
            boolean hover = over(mx, my, x, y, rw, 14);
            if (index == category) {
                g.fill(x, y, x + rw, y + 14, c(Token.SIDE_SEL_BG));
            } else if (hover) {
                g.fill(x, y, x + rw, y + 14, c(Token.ROW_HOVER));
            }
            text(g, trim(categoryName(index), rw - 8), x + 4, midY(y, 14),
                    index == category ? c(Token.SIDE_SEL_TEXT) : c(Token.DIALOG_TEXT));
            addHit(x, y, rw, 14, () -> {
                category = index;
                browsePage = 0;
                overlay = Overlay.NONE;
            });
            y += 16;
        }
        if (menuTotal > menuFit) {
            int trackH = menuFit * 16 - 2;
            int barH = Math.max(8, trackH * menuFit / menuTotal);
            int barY = d[1] + 8 + (trackH - barH) * menuScroll / (menuTotal - menuFit);
            int barX = d[0] + d[2] - 6;
            g.fill(barX, d[1] + 8, barX + 2, d[1] + 8 + trackH, c(Token.SIDE_LINE));
            g.fill(barX, barY, barX + 2, barY + barH, c(Token.SIDE_DIM));
        }
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        if (overlay == Overlay.NONE || overlay == Overlay.REPORT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY < 0 ? 1 : -1;
        if (overlay == Overlay.CATEGORIES) {
            menuScroll = Math.max(0, Math.min(Math.max(0, menuTotal - menuFit), menuScroll + step));
            return true;
        }
        if (overlay == Overlay.NONE && view == View.BROWSE && !lay.collapsed() && mouseX < lay.sideW()
                && mouseY >= lay.headerH()) {
            sideScroll = Math.max(0, Math.min(Math.max(0, sideTotal - sideFit), sideScroll + step));
            return true;
        }
        if (overlay == Overlay.NONE && view == View.BROWSE) {
            int per = lay.rows();
            int pages = Math.max(1, (filteredRows().size() + per - 1) / per);
            browsePage = Math.max(0, Math.min(pages - 1, browsePage + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && overlay != Overlay.NONE) {
            overlay = Overlay.NONE;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (overlay == Overlay.REPORT && reason.isFocused() && target != null) {
                ClientData.send(new MarketPackets.ReportRequest(target.id(), reason.getValue()));
                overlay = Overlay.NONE;
                return true;
            }
        }
        if (overlay != Overlay.NONE && overlay != Overlay.REPORT) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
