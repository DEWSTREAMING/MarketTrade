package com.markettrade.market;

import java.util.UUID;

/** One finished sale. No Minecraft classes. */
public record Sale(long time, UUID seller, String sellerName, UUID buyer, String buyerName, String itemId, int count,
        long price, long cut) {
}
