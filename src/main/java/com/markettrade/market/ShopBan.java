package com.markettrade.market;

import java.util.UUID;

/** A ban from the market only. {@code bannedBy} is null when the console did it. */
public record ShopBan(UUID player, String name, UUID bannedBy, String bannedByName, String reason, long time) {
}
