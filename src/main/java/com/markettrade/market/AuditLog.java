package com.markettrade.market;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Writes audit lines into the world folder, so a pack update never touches them. Called on the server thread from the one place
 * each thing happens. A failed write is logged and never undoes or blocks a trade.
 */
public final class AuditLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AuditLog() {
    }

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("markettrade").resolve("audit");
    }

    /** Never throws: this runs in the middle of a trade, after money has moved, and a mod's odd item must not stop it there. */
    public static AuditItem item(MinecraftServer server, StoredStack stored) {
        try {
            if (!stored.isAvailable()) {
                return new AuditItem(stored.itemId(), stored.count(), stored.itemId(), java.util.List.of(), stored.raw().toString());
            }
            net.minecraft.world.item.ItemStack stack = stored.stack();
            return new AuditItem(stored.itemId(), stack.getCount(), stack.getHoverName().getString(), ItemTexts.collect(stack),
                    stack.save(server.registryAccess()).toString());
        } catch (RuntimeException e) {
            LOGGER.error("Could not read the item {} for the audit log. Keeping its id only.", stored.itemId(), e);
            return new AuditItem(stored.itemId(), stored.count(), stored.itemId(), java.util.List.of(), "");
        }
    }

    public static void log(MinecraftServer server, AuditEntry entry) {
        try {
            AuditFiles.append(dir(server), entry);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not write the audit log ({} by {}). The trade still went through.", entry.event(),
                    entry.actorName(), e);
        }
        LOGGER.info("Audit {}", summary(entry));
    }

    /** One console line. Control characters in names are replaced so a name can't start a fake log line. */
    static String summary(AuditEntry e) {
        StringBuilder out = new StringBuilder(e.event());
        if (e.actorName() != null) {
            out.append(" by ").append(e.actorName());
        }
        if (e.targetName() != null) {
            out.append(" target ").append(e.targetName());
        }
        if (e.listing() != null) {
            out.append(" listing ").append(e.listing());
        }
        if (e.price() != null) {
            out.append(" price ").append(e.price());
        }
        if (e.item() != null) {
            out.append(" item ").append(e.item().id()).append(" x").append(e.item().count()).append(" \"")
                    .append(e.item().name()).append('"');
        }
        if (e.reason() != null && !e.reason().isEmpty()) {
            out.append(" reason \"").append(e.reason()).append('"');
        }
        String line = out.toString().replaceAll("\\p{Cntrl}", " ");
        return line.length() > 400 ? line.substring(0, 400) + "..." : line;
    }
}
