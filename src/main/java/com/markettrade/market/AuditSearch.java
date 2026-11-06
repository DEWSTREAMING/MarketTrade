package com.markettrade.market;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;

/** Reads the audit files off the server thread and hands the page back on it. */
public final class AuditSearch {
    public static final int PAGE_SIZE = 10;
    private static final Logger LOGGER = LogUtils.getLogger();

    private AuditSearch() {
    }

    /** {@code done} runs on the server thread with the page, or null if the read failed. */
    public static void history(MinecraftServer server, UUID player, int page, Consumer<AuditFiles.Page> done) {
        run(server, e -> e.involves(player), page, done);
    }

    public static void find(MinecraftServer server, String text, int page, Consumer<AuditFiles.Page> done) {
        run(server, e -> e.itemTextContains(text), page, done);
    }

    private static void run(MinecraftServer server, Predicate<AuditEntry> filter, int page, Consumer<AuditFiles.Page> done) {
        java.nio.file.Path dir = AuditLog.dir(server);
        int skip = Math.max(0, page - 1) * PAGE_SIZE;
        CompletableFuture.supplyAsync(() -> AuditFiles.read(dir, filter, skip, PAGE_SIZE, LOGGER::warn))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null) {
                        LOGGER.error("Audit search failed", error);
                        done.accept(null);
                    } else {
                        done.accept(result);
                    }
                }));
    }
}
