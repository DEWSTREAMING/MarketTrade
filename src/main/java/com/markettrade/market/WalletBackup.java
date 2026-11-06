package com.markettrade.market;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Copies a file aside before anything can overwrite it. */
public final class WalletBackup {
    private WalletBackup() {
    }

    /** Returns the new path. The copy sits in the same folder and never replaces an earlier copy. */
    public static Path copyAside(Path file, LocalDateTime now) throws IOException {
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path target = file.resolveSibling(base + "-broken-" + stamp + ext);
        int n = 1;
        while (Files.exists(target)) {
            target = file.resolveSibling(base + "-broken-" + stamp + "-" + n++ + ext);
        }
        Files.copy(file, target);
        return target;
    }
}
