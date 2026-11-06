package com.markettrade.market;

import java.util.UUID;

/**
 * One time money left the pool or the pool was set by hand. The actor and target ids are null for the console and for an
 * adjustment. No Minecraft classes.
 */
public record PoolEntry(long time, Kind kind, long amount, long poolAfter, UUID actor, String actorName, UUID target,
        String targetName, String note) {
    public enum Kind {
        PAYOUT, ADJUST
    }
}
