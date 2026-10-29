package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonParser;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateResultTest {

    private static final String VERSION_JSON = """
            {
              "id": "abc123",
              "version_number": "1.2.1",
              "name": "Example Mod 1.2.1",
              "version_type": "release",
              "status": "listed",
              "date_published": "2026-01-02T03:04:05.678Z",
              "files": [
                { "url": "https://cdn.example.com/secondary.jar", "size": 1000, "filename": "secondary.jar" },
                { "url": "https://cdn.example.com/primary.jar", "size": 2048, "filename": "primary.jar", "primary": true }
              ]
            }
            """;

    private static UpdateResult resultWithCurrent(String json, String current) {
        return UpdateResult.from(JsonParser.parseString(json).getAsJsonObject(), current, false);
    }

    @Test
    void parsesAllFields() {
        UpdateResult result = resultWithCurrent(VERSION_JSON, "1.2.0");

        assertEquals("1.2.0", result.currentVersion());
        assertEquals("1.2.1", result.latestVersion());
        assertEquals("abc123", result.versionId());
        assertEquals("Example Mod 1.2.1", result.versionName().orElseThrow());
        assertEquals("release", result.versionType().orElseThrow());
        assertEquals(Instant.ofEpochMilli(1767323045678L), result.publishedAt().orElseThrow());
        assertEquals("https://cdn.example.com/primary.jar", result.downloadUrl().orElseThrow());
        assertEquals("primary.jar", result.fileName().orElseThrow());
        assertEquals(2048L, result.fileSize().orElseThrow());
        assertTrue(result.changelog().isEmpty(), "changelog is not requested by default");
    }

    @Test
    void keepsChangelogWhenRequested() {
        String json = """
                {
                  "id": "id1",
                  "version_number": "2.0.0",
                  "changelog": "## Fixed\\n- Everything"
                }
                """;
        UpdateResult result = UpdateResult.from(
                JsonParser.parseString(json).getAsJsonObject(), "1.0.0", true);
        assertEquals("## Fixed\n- Everything", result.changelog().orElseThrow());
    }

    @Test
    void optionalFieldsAreEmptyWhenMissing() {
        String json = """
                {
                  "id": "id1",
                  "version_number": "2.0.0"
                }
                """;
        UpdateResult result = resultWithCurrent(json, "1.0.0");

        assertTrue(result.versionName().isEmpty());
        assertTrue(result.versionType().isEmpty());
        assertTrue(result.publishedAt().isEmpty());
        assertTrue(result.downloadUrl().isEmpty());
        assertTrue(result.fileName().isEmpty());
        assertTrue(result.fileSize().isEmpty());
        assertTrue(result.changelog().isEmpty());
    }

    @Test
    void fallsBackToFirstFileWhenNoneIsPrimary() {
        String json = """
                {
                  "id": "id1",
                  "version_number": "1.0.0",
                  "files": [
                    { "url": "https://cdn.example.com/a.jar", "size": 1, "filename": "a.jar", "primary": false },
                    { "url": "https://cdn.example.com/b.jar", "size": 2, "filename": "b.jar" }
                  ]
                }
                """;
        UpdateResult result = resultWithCurrent(json, "0.9");
        assertEquals("https://cdn.example.com/a.jar", result.downloadUrl().orElseThrow());
        assertEquals("a.jar", result.fileName().orElseThrow());
        assertEquals(1L, result.fileSize().orElseThrow());
    }

    @Test
    void updateAvailableWhenCurrentIsOlder() {
        UpdateResult result = resultWithCurrent(
                """
                { "id": "id1", "version_number": "1.2.1" }
                """,
                "1.2.0");
        assertTrue(result.isUpdateAvailable());
    }

    @Test
    void noUpdateAvailableWhenCurrentMatchesIgnoringPrefix() {
        UpdateResult result = resultWithCurrent(
                """
                { "id": "id1", "version_number": "1.2.1" }
                """,
                "v1.2.1");
        assertFalse(result.isUpdateAvailable());
    }

    /**
     * A dev build newer than anything published on Modrinth must not be
     * flagged as outdated.
     */
    @Test
    void noUpdateAvailableWhenCurrentIsNewerThanLatest() {
        UpdateResult result = resultWithCurrent(
                """
                { "id": "id1", "version_number": "1.2.1" }
                """,
                "2.0.0-dev");
        assertFalse(result.isUpdateAvailable());
    }

    @Test
    void updateAvailableWhenCurrentPreReleaseOlderThanRelease() {
        UpdateResult result = resultWithCurrent(
                """
                { "id": "id1", "version_number": "1.2.1" }
                """,
                "1.2.1-beta");
        assertTrue(result.isUpdateAvailable());
    }

    @Test
    void rejectsMalformedVersionObjects() {
        assertThrows(ModrinthException.class,
                () -> resultWithCurrent("{ \"version_number\": \"1.2.1\" }", "1.0.0"));
        assertThrows(ModrinthException.class,
                () -> resultWithCurrent("{ \"id\": \"id1\" }", "1.0.0"));
    }
}
