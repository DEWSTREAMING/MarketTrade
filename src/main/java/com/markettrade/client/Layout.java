package com.markettrade.client;

/**
 * Sizes that depend on the window, in GUI units. The screen asks this how many rows fit, so the same code works from a tiny window
 * to a big one. No Minecraft classes.
 */
public record Layout(int width, int height, int headerH, int footerH, int pad, int sideW, boolean collapsed,
        int colHeadH, int rowH, int rows, int infoH, int listingH, int listingRows, int stripH) {
    public static final int HEADER_H = 24;
    public static final int FOOTER_H = 20;
    public static final int PAD = 6;
    public static final int COL_HEAD_H = 12;
    public static final int ROW_H = 22;
    public static final int ROW_GAP = 2;
    public static final int INFO_H = 64;
    public static final int LISTING_H = 26;
    /** Below this width the category column turns into a button. */
    public static final int COLLAPSE_BELOW = 400;
    public static final int MAX_LISTING_ROWS = 12;
    /** Search and category row that replaces the category column when the window is narrow. */
    public static final int STRIP_H = 22;

    public static Layout compute(int width, int height) {
        int sideW = width >= COLLAPSE_BELOW ? Math.max(88, Math.min(140, width / 5)) : 0;
        int listTop = HEADER_H + PAD + COL_HEAD_H;
        int listBottom = height - FOOTER_H - PAD;
        int stripH = sideW == 0 ? STRIP_H : 0;
        int rows = Math.max(1, (listBottom - listTop - stripH) / (ROW_H + ROW_GAP));
        int listingTop = HEADER_H + PAD + INFO_H + PAD + COL_HEAD_H;
        int listingRows = Math.max(1, Math.min(MAX_LISTING_ROWS, (listBottom - listingTop) / LISTING_H));
        return new Layout(width, height, HEADER_H, FOOTER_H, PAD, sideW, sideW == 0, COL_HEAD_H, ROW_H, rows, INFO_H,
                LISTING_H, listingRows, stripH);
    }

    /** Top of the first row on Browse and the other lists. */
    public int listTop() {
        return headerH + pad + colHeadH + stripH;
    }

    public int listLeft() {
        return sideW + pad;
    }

    public int listRight() {
        return width - pad;
    }

    public int footerTop() {
        return height - footerH;
    }

    public int rowStep() {
        return rowH + ROW_GAP;
    }
}
