package com.markettrade.market;

import java.util.UUID;

/** Who did something. {@code id} is null for the console. */
public record Actor(UUID id, String name) {
    public static Actor named(String name) {
        return new Actor(null, name);
    }
}
