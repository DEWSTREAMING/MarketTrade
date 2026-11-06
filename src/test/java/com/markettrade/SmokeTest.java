package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void modIdMatchesGradleProperties() throws Exception {
        assertTrue(Files.readString(Path.of("gradle.properties")).contains("mod_id=" + "markettrade"));
    }
}
