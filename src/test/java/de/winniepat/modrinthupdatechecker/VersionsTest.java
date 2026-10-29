package de.winniepat.modrinthupdatechecker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    private static void assertEqualsVersions(String left, String right) {
        assertEquals(0, Versions.compare(left, right), left + " should equal " + right);
        assertEquals(0, Versions.compare(right, left), right + " should equal " + left);
    }

    private static void assertNewer(String newer, String older) {
        assertTrue(Versions.compare(newer, older) > 0, newer + " should be newer than " + older);
        assertTrue(Versions.compare(older, newer) < 0, older + " should be older than " + newer);
    }

    @Test
    void ignoresLeadingV() {
        assertEqualsVersions("v1.2.0", "1.2.0");
        assertEqualsVersions("V1.2.0", "1.2.0");
    }

    @Test
    void comparesNumericallyNotLexicographically() {
        assertNewer("1.2.10", "1.2.9");
        assertNewer("1.10.0", "1.9.0");
    }

    @ParameterizedTest(name = "{0} newer than {1}")
    @CsvSource({
            "1.2.1, 1.2.0",
            "2.0.0, 1.9.9",
            "1.0.0, 0.9.9",
    })
    void comparesCoreVersions(String newer, String older) {
        assertNewer(newer, older);
    }

    @Test
    void treatsMissingTrailingPartsAsZero() {
        assertEqualsVersions("1.2", "1.2.0");
        assertEqualsVersions("1.2", "1.2.0.0");
        assertNewer("1.2.1", "1.2");
        assertNewer("1.3", "1.2.99");
    }

    @Test
    void releaseBeatsPreReleaseOfSameCoreVersion() {
        assertNewer("1.2.0", "1.2.0-beta");
        assertNewer("1.2.0", "1.2.0-SNAPSHOT");
        assertNewer("1.2.0", "1.2.0-alpha.2");
    }

    @Test
    void ordersPreReleaseIdentifiersSemantically() {
        assertNewer("1.0.0-beta.11", "1.0.0-beta.2");
        assertNewer("1.0.0-alpha.2", "1.0.0-alpha.1");
        assertNewer("1.0.0-beta", "1.0.0-alpha");
        assertNewer("1.0.0-alpha.1", "1.0.0-alpha");
    }

    @Test
    void ignoresCaseForIdentifiers() {
        assertEqualsVersions("1.0.0-BETA", "1.0.0-beta");
        assertEqualsVersions("1.0.0-Awesome", "1.0.0-awesome");
    }

    @Test
    void ignoresBuildMetadata() {
        assertEqualsVersions("1.2.0+build.42", "1.2.0");
        assertEqualsVersions("1.0.0-alpha+build.5", "1.0.0-alpha");
    }

    @Test
    void trimsWhitespace() {
        assertEqualsVersions(" 1.2.0 ", "1.2.0");
    }

    @Test
    void returnsZeroForIdenticalRawStrings() {
        assertEqualsVersions("same", "same");
        assertEqualsVersions("", "");
        assertEqualsVersions(null, null);
    }

    @Test
    void unparseableVersionsFallBackToStringComparison() {
        assertTrue(Versions.compare("aurora", "1.2.0") > 0, "deterministic fallback ordering");
        assertTrue(Versions.compare("Zulu", "aurora") > 0, "case-insensitive fallback ordering");
        assertEquals(-1, Versions.compare("", "1.2.0"), "empty counts as oldest");
        assertEquals(1, Versions.compare("1.2.0", ""), "nothing is newer than empty");
    }

    @Test
    void isOlderMatchesCompare() {
        assertTrue(Versions.isOlder("1.2.0", "1.2.1"));
        assertTrue(Versions.isOlder("1.2.0-beta", "1.2.0"));
        assertTrue(!Versions.isOlder("1.2.1", "1.2.0"));
        assertTrue(!Versions.isOlder("1.2.0", "1.2.0"));
    }
}
