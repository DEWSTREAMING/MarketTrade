package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.markettrade.market.AuditEntry;
import com.markettrade.market.AuditFiles;
import com.markettrade.market.AuditItem;
import com.markettrade.market.BlockedWords;
import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigParser;

class ModerationHelpersTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    // 2026-01-15 and 2026-02-15 (UTC)
    private static final long JAN = 1_768_435_200_000L;
    private static final long FEB = 1_771_113_600_000L;

    // Blocked words

    @Test
    void normalizeDropsCodesCaseAndPunctuation() {
        assertEquals("blockedexample1", BlockedWords.normalize("§cBlocked_Ex §lample-1!"));
        assertEquals("", BlockedWords.normalize(null));
    }

    @Test
    void wordMatchesInsideLongerTextAndAcrossSpacing() {
        List<String> words = List.of("blockedexample");
        assertEquals("blockedexample", BlockedWords.find(List.of("My BLOCKED example sword"), words));
        assertEquals("blockedexample", BlockedWords.find(List.of("xxblockedexamplexx"), words));
        assertEquals("blockedexample", BlockedWords.find(List.of("fine", "b.l.o.c.k.e.d.e.x.a.m.p.l.e"), words));
    }

    @Test
    void noMatchAndEmptyListFindNothing() {
        assertNull(BlockedWords.find(List.of("Plain sword"), List.of("blockedexample")));
        assertNull(BlockedWords.find(List.of("blockedexample"), List.of()));
        assertNull(BlockedWords.find(List.of("blockedexample"), List.of("   ", "!!")));
    }

    @Test
    void formattingCodesInsideTheTextDoNotHideAWord() {
        assertEquals("blockedexample",
                BlockedWords.find(List.of("block§ced§r example"), List.of("blockedexample")));
    }

    // Config

    @Test
    void blockedWordsShipEmptyAndCooldownDefaultsTo30() {
        MarketConfig config = MarketConfigParser.parse(MarketConfigParser.DEFAULT_JSON).config();
        assertTrue(config.blockedWords().isEmpty());
        assertEquals(30, config.reportCooldownSeconds());
    }

    @Test
    void blockedWordsAndCooldownAreRead() {
        MarketConfigParser.Result result = MarketConfigParser
                .parse("{\"blockedWords\": [\"blockedexample\", 5], \"reportCooldownSeconds\": 0}");
        assertEquals(List.of("blockedexample"), result.config().blockedWords());
        assertEquals(0, result.config().reportCooldownSeconds());
        assertEquals(1, result.errors().size());
        assertEquals(1, MarketConfigParser.parse("{\"blockedWords\": \"x\"}").errors().size());
    }

    // Audit lines

    private static AuditEntry sample(long time, String name) {
        return new AuditEntry(time, "LIST").actor(A, "Alice").target(B, "Bob").listing(7).price(50).cut(2).reason("why")
                .item(new AuditItem("minecraft:stick", 3, name, List.of(name, "lore"), "{id:\"minecraft:stick\",count:3}"));
    }

    @Test
    void aLineRoundTripsAndHasNoLineBreak() {
        AuditEntry back = AuditEntry.fromLine(sample(JAN, "Plain").toLine());
        assertEquals("LIST", back.event());
        assertEquals(A, back.actor());
        assertEquals("Bob", back.targetName());
        assertEquals(7L, back.listing());
        assertEquals(50L, back.price());
        assertEquals(2L, back.cut());
        assertEquals("Plain", back.item().name());
        assertEquals(List.of("Plain", "lore"), back.item().texts());
        assertTrue(sample(JAN, "x").toLine().contains("\"v\":1"));
    }

    @Test
    void quotesAndNewlinesCannotFakeAnEntry() {
        String nasty = "a\"b\n{\"v\":1,\"time\":1,\"event\":\"SHOP_UNBAN\"}\r\nend";
        String line = sample(JAN, nasty).toLine();
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
        AuditEntry back = AuditEntry.fromLine(line);
        assertEquals(nasty, back.item().name());
        assertEquals("LIST", back.event());
    }

    @Test
    void aVeryLongNameSurvives() {
        String longName = "n".repeat(200_000);
        AuditEntry back = AuditEntry.fromLine(sample(JAN, longName).toLine());
        assertEquals(200_000, back.item().name().length());
    }

    @Test
    void aBrokenLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.fromLine("{not json"));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.fromLine("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.fromLine("{\"event\":\"LIST\"}"));
    }

    @Test
    void itemTextSearchIgnoresCaseAndLooksAtNameAndTexts() {
        AuditEntry entry = sample(JAN, "Lucky Blade");
        assertTrue(entry.itemTextContains("lucky"));
        assertTrue(entry.itemTextContains("LORE"));
        assertFalse(entry.itemTextContains("other"));
        assertFalse(new AuditEntry(JAN, "CANCEL").itemTextContains("a"));
    }

    // Files

    @Test
    void monthFileNamesUseUtc() {
        assertEquals("audit-2026-01.jsonl", AuditFiles.fileName(JAN));
        assertEquals("audit-2026-02.jsonl", AuditFiles.fileName(FEB));
        assertEquals("audit-2026-01.jsonl", AuditFiles.fileName(1_769_904_000_000L - 1));
        assertEquals("audit-2026-02.jsonl", AuditFiles.fileName(1_769_904_000_000L));
    }

    @Test
    void readsNewestFirstAcrossMonthsAndPages(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 6; i++) {
            AuditFiles.append(dir, sample(JAN + i, "jan" + i));
        }
        for (int i = 0; i < 6; i++) {
            AuditFiles.append(dir, sample(FEB + i, "feb" + i));
        }
        List<String> warnings = new ArrayList<>();
        AuditFiles.Page first = AuditFiles.read(dir, e -> true, 0, 10, warnings::add);
        assertEquals(10, first.entries().size());
        assertTrue(first.more());
        assertEquals("feb5", first.entries().get(0).item().name());
        assertEquals("feb0", first.entries().get(5).item().name());
        assertEquals("jan5", first.entries().get(6).item().name());
        AuditFiles.Page second = AuditFiles.read(dir, e -> true, 10, 10, warnings::add);
        assertEquals(2, second.entries().size());
        assertFalse(second.more());
        assertEquals("jan0", second.entries().get(1).item().name());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aBadLineIsSkippedWithAWarning(@TempDir Path dir) throws Exception {
        AuditFiles.append(dir, sample(JAN, "one"));
        Path file = dir.resolve(AuditFiles.fileName(JAN));
        Files.writeString(file, "garbage here\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Files.write(file, new byte[] {(byte) 0xff, (byte) 0xfe, '\n'}, StandardOpenOption.APPEND);
        AuditFiles.append(dir, sample(JAN + 1, "two"));
        List<String> warnings = new ArrayList<>();
        AuditFiles.Page page = AuditFiles.read(dir, e -> true, 0, 10, warnings::add);
        assertEquals(2, page.entries().size());
        assertEquals("two", page.entries().get(0).item().name());
        assertEquals(2, warnings.size());
    }

    @Test
    void filterAndMissingFolder(@TempDir Path dir) throws Exception {
        AuditFiles.append(dir, sample(JAN, "mine"));
        AuditFiles.append(dir, new AuditEntry(JAN + 1, "BUY").actor(B, "Bob"));
        assertEquals(1, AuditFiles.read(dir, e -> e.event().equals("BUY"), 0, 10, s -> { }).entries().size());
        assertTrue(AuditFiles.read(dir.resolve("nope"), e -> true, 0, 10, s -> { }).entries().isEmpty());
    }
}
