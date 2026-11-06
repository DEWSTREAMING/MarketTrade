package com.markettrade.gui;

import com.markettrade.market.SortMode;

/** What a player has chosen on the Browse page. It survives going to the anvil and back. */
final class BrowseState {
    SortMode sort = SortMode.NEWEST;
    String search = "";
    int page;
    int totalPages = 1;
}
