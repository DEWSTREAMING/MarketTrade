package com.markettrade.market;

/**
 * One line of Browse: an item id, how many listings there are and the lowest price. Only ids and numbers, never a stack, so a
 * custom name or other data can't reach this row. {@code available} is false when the item's mod is missing.
 */
public record SummaryRow(String itemId, int listed, long lowest, String lowestText, boolean available) {
}
