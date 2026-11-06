package com.markettrade;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Text {
    private Text() {
    }

    public static MutableComponent translatable(String key, Object... args) {
        String fallback = Language.getInstance().getOrDefault(key);
        return Component.translatableWithFallback(key, fallback, args);
    }
}
