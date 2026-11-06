package com.markettrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Sorting and searching for the listing screens. No Minecraft classes, so it can be tested on its own. */
public final class ListingSort {
    private ListingSort() {
    }

    /** A new list. Ties fall back to the newest id first, so the order never flickers. */
    public static <T> List<T> sorted(List<T> items, SortMode mode, ToLongFunction<T> id, ToLongFunction<T> price,
            ToLongFunction<T> expiresAt, Function<T, String> name) {
        Comparator<T> newest = Comparator.comparingLong(id).reversed();
        Comparator<T> order = switch (mode) {
            case NEWEST -> newest;
            case CHEAPEST -> Comparator.comparingLong(price).thenComparing(newest);
            case DEAREST -> Comparator.comparingLong(price).reversed().thenComparing(newest);
            case ENDING_SOON -> Comparator.comparingLong(expiresAt).thenComparing(newest);
            case NAME -> Comparator.comparing((T item) -> name.apply(item).toLowerCase(Locale.ROOT)).thenComparing(newest);
        };
        List<T> copy = new ArrayList<>(items);
        copy.sort(order);
        return copy;
    }

    /** Case-insensitive. The text can be part of the shown name or of the item id. An empty search matches everything. */
    public static boolean matches(String shownName, String itemId, String query) {
        if (query == null) {
            return true;
        }
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return true;
        }
        return (shownName != null && shownName.toLowerCase(Locale.ROOT).contains(wanted))
                || (itemId != null && itemId.toLowerCase(Locale.ROOT).contains(wanted));
    }
}
