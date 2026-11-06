package com.markettrade.market;

import java.util.UUID;

/** One item for sale. The price is for the whole stack. */
public record Listing(long id, UUID seller, String sellerName, StoredStack item, long price, long expiresAt) {
}
