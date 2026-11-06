package com.markettrade.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The audit folder: one file per UTC month, appended to and never edited or deleted by the mod. No Minecraft classes.
 */
public final class AuditFiles {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final String PREFIX = "audit-";
    private static final String SUFFIX = ".jsonl";

    /** {@code more} is true if there are matching entries after this page. */
    public record Page(List<AuditEntry> entries, boolean more) {
    }

    private AuditFiles() {
    }

    public static String fileName(long time) {
        return PREFIX + MONTH.format(Instant.ofEpochMilli(time)) + SUFFIX;
    }

    /** Adds one line to the month's file. The file is closed, so the line is written out when this returns. */
    public static void append(Path dir, AuditEntry entry) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName(entry.time()));
        Files.writeString(file, entry.toLine() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    /**
     * Matching entries, newest first, skipping {@code skip} of them and returning at most {@code limit}. Newest month first, and it
     * stops reading once the page is full. A line that can't be read is skipped and reported to {@code warn}.
     */
    public static Page read(Path dir, Predicate<AuditEntry> filter, int skip, int limit, Consumer<String> warn) {
        List<AuditEntry> found = new ArrayList<>();
        int seen = 0;
        for (Path file : monthFilesNewestFirst(dir)) {
            String[] lines;
            try {
                // Decoding replaces a damaged byte instead of throwing, so one bad byte never stops the read.
                lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n");
            } catch (IOException e) {
                warn.accept("Could not read " + file.getFileName() + ": " + e.getMessage());
                continue;
            }
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].strip();
                if (line.isEmpty()) {
                    continue;
                }
                AuditEntry entry;
                try {
                    entry = AuditEntry.fromLine(line);
                } catch (IllegalArgumentException e) {
                    warn.accept("Skipping a bad line in " + file.getFileName() + " (line " + (i + 1) + "): " + e.getMessage());
                    continue;
                }
                if (!filter.test(entry)) {
                    continue;
                }
                if (seen++ < skip) {
                    continue;
                }
                if (found.size() == limit) {
                    return new Page(found, true);
                }
                found.add(entry);
            }
        }
        return new Page(found, false);
    }

    private static List<Path> monthFilesNewestFirst(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
            }).forEach(files::add);
        } catch (IOException e) {
            return List.of();
        }
        files.sort(Collections.reverseOrder());
        return files;
    }
}
