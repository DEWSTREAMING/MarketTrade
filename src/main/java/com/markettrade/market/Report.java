package com.markettrade.market;

import java.util.UUID;

/** A player report. It keeps its own copy of the stack, so it outlives the listing. */
public record Report(long id, long time, UUID reporter, String reporterName, UUID seller, String sellerName, long listingId,
        StoredStack item, String reason, boolean open) {
    public Report closed() {
        return new Report(id, time, reporter, reporterName, seller, sellerName, listingId, item, reason, false);
    }
}
