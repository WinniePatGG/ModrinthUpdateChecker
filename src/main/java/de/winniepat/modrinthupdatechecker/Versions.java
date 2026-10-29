package de.winniepat.modrinthupdatechecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Small, dependency-free version comparator used to decide whether the
 * latest Modrinth release is actually newer than the running version.
 *
 * <p>It implements the parts of Semantic Versioning that matter in practice
 * while tolerating the loose version strings found on Modrinth:
 * <ul>
 *   <li>A leading {@code v}/{@code V} is ignored ({@code v1.2.0 == 1.2.0}).</li>
 *   <li>Numeric identifiers are compared numerically, not alphabetically
 *       ({@code 1.10.0 > 1.9.0}).</li>
 *   <li>Missing or extra trailing zero identifiers count as equal
 *       ({@code 1.2 == 1.2.0 == 1.2.0.0}).</li>
 *   <li>A pre-release ({@code -alpha.2}, {@code -beta}, {@code -Snapshot}) is
 *       older than the final release of the same core version.</li>
 *   <li>Build metadata ({@code +build.42}) is ignored.</li>
 *   <li>Identifiers are compared case-insensitively.</li>
 * </ul>
 *
 * <p>Version strings that are unparseable fall back to plain string
 * comparison; behavior for such inputs is best effort.
 */
final class Versions {

    private Versions() {
    }

    /**
     * Returns {@code true} if {@code candidate} is strictly older than
     * {@code reference}.
     */
    static boolean isOlder(String candidate, String reference) {
        return compare(candidate, reference) < 0;
    }

    /**
     * Compares two version strings. Returns a negative value if {@code a}
     * is older than {@code b}, zero if they are equal, and a positive value
     * if {@code a} is newer than {@code b}.
     */
    static int compare(String aRaw, String bRaw) {
        Parsed a = parse(aRaw);
        Parsed b = parse(bRaw);

        if (a == null || b == null) {
            // Unparseable input: deterministic plain string comparison as fallback.
            String left = Objects.requireNonNullElse(aRaw, "");
            String right = Objects.requireNonNullElse(bRaw, "");
            if (left.equals(right)) {
                return 0;
            }
            if (left.isEmpty()) {
                return -1;
            }
            if (right.isEmpty()) {
                return 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(left, right);
        }

        int main = compareIdentifiers(a.core(), b.core());
        if (main != 0) {
            return main;
        }
        return comparePreRelease(a.preRelease(), b.preRelease());
    }

    /**
     * Comparison of dot-separated identifier lists. Once one list is a
     * prefix of the other, trailing zero identifiers count as equal
     * ({@code 1.2 == 1.2.0}) and any other extra identifier makes the longer
     * list newer.
     */
    private static int compareIdentifiers(List<String> left, List<String> right) {
        int min = Math.min(left.size(), right.size());
        for (int i = 0; i < min; i++) {
            int result = compareSingle(left.get(i), right.get(i));
            if (result != 0) {
                return result;
            }
        }
        for (int i = min; i < left.size(); i++) {
            if (outranksImplicitZero(left.get(i))) {
                return 1;
            }
        }
        for (int i = min; i < right.size(); i++) {
            if (outranksImplicitZero(right.get(i))) {
                return -1;
            }
        }
        return 0;
    }

    /**
     * Semantic Versioning pre-release rules: a release beats any pre-release
     * with the same core version; otherwise pre-release identifiers are
     * compared left to right.
     */
    private static int comparePreRelease(List<String> left, List<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0;
        }
        if (left.isEmpty()) {
            return 1;
        }
        if (right.isEmpty()) {
            return -1;
        }
        return compareIdentifiers(left, right);
    }

    /**
     * Numeric identifiers compare numerically and sort below alphanumeric
     * identifiers; alphanumeric identifiers compare case-insensitively.
     */
    private static int compareSingle(String left, String right) {
        Long leftNumber = tryNumber(left);
        Long rightNumber = tryNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return Long.compare(leftNumber, rightNumber);
        }
        if (leftNumber != null) {
            return -1;
        }
        if (rightNumber != null) {
            return 1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(left, right);
    }

    /**
     * Returns {@code true} if the identifier is more than just another zero,
     * i.e. it makes a version with it as an extra trailing identifier newer.
     */
    private static boolean outranksImplicitZero(String identifier) {
        Long numeric = tryNumber(identifier);
        if (numeric != null) {
            return numeric != 0;
        }
        // Alphanumeric identifiers always outrank the implicit "0".
        return true;
    }

    private static Long tryNumber(String value) {
        if (value.isEmpty() || !Character.isDigit(value.charAt(0))) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Parsed(List<String> core, List<String> preRelease) {
    }

    /**
     * Parses a loose version string into main identifiers and pre-release
     * identifiers, or returns {@code null} if the string is not usable.
     */
    private static Parsed parse(String raw) {
        if (raw == null) {
            return null;
        }
        String version = raw.strip();
        if (version.isEmpty()) {
            return null;
        }
        // Drop a common leading "v"/"V" prefix, e.g. "v1.2.0".
        if (version.length() > 1
                && (version.charAt(0) == 'v' || version.charAt(0) == 'V')
                && Character.isDigit(version.charAt(1))) {
            version = version.substring(1);
        }
        // Build metadata is ignored for precedence.
        int buildIndex = version.indexOf('+');
        if (buildIndex >= 0) {
            version = version.substring(0, buildIndex);
        }

        String preRelease = null;
        int preIndex = version.indexOf('-');
        if (preIndex >= 0) {
            preRelease = version.substring(preIndex + 1);
            version = version.substring(0, preIndex);
        }
        if (version.isEmpty() || version.endsWith(".")) {
            return null;
        }

        List<String> core = splitIdentifiers(version);
        List<String> pre = preRelease == null ? List.of() : splitIdentifiers(preRelease);
        for (String identifier : core) {
            if (identifier.isEmpty()) {
                return null;
            }
        }
        for (String identifier : pre) {
            if (identifier.isEmpty()) {
                return null;
            }
        }
        return new Parsed(core, pre);
    }

    private static List<String> splitIdentifiers(String part) {
        String normalized = part.toLowerCase();
        List<String> identifiers = new ArrayList<>();
        int start = 0;
        int length = normalized.length();
        while (start < length) {
            int next = normalized.indexOf('.', start);
            if (next == -1) {
                identifiers.add(normalized.substring(start));
                break;
            }
            identifiers.add(normalized.substring(start, next));
            start = next + 1;
        }
        return Collections.unmodifiableList(identifiers);
    }
}
