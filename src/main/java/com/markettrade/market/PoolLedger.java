package com.markettrade.market;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** The newest pool entries, oldest dropped first. No Minecraft classes. */
public final class PoolLedger {
    public static final int KEEP = 1000;

    private final Deque<PoolEntry> entries = new ArrayDeque<>();

    public void add(PoolEntry entry) {
        entries.addLast(entry);
        while (entries.size() > KEEP) {
            entries.removeFirst();
        }
    }

    /** Oldest first, the order it is saved in. */
    public List<PoolEntry> oldestFirst() {
        return List.copyOf(entries);
    }

    public List<PoolEntry> newestFirst() {
        List<PoolEntry> list = new ArrayList<>(entries);
        Collections.reverse(list);
        return list;
    }

    public int size() {
        return entries.size();
    }
}
