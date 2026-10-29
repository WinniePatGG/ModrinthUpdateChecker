package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The outcome of an update check: the newest released version of the target
 * Modrinth project.
 *
 * <p>Fields without a value on Modrinth (or disabled by the
 * {@link ModrinthUpdateChecker.Builder}, e.g. the changelog) are reported as
 * empty {@link Optional}s.
 */
public final class UpdateResult {

    private final String currentVersion;
    private final String latestVersion;
    private final String versionId;
    private final String versionName;
    private final String versionType;
    private final Instant publishedAt;
    private final String downloadUrl;
    private final String fileName;
    private final Long fileSize;
    private final String changelog;

    /**
     * Creates a minimal result. Prefer
     * {@link ModrinthUpdateChecker#checkForUpdates()} unless you are
     * constructing results yourself (e.g. in tests); the full constructor is
     * available for every field this library uses.
     *
     * @param currentVersion the version the caller is currently running
     * @param latestVersion the version number of the newest release
     * @param versionId the Modrinth-internal version ID
     * @param downloadUrl the primary download URL, or {@code null}
     */
    public UpdateResult(
            String currentVersion,
            String latestVersion,
            String versionId,
            String downloadUrl
    ) {
        this(currentVersion, latestVersion, versionId, null, null, null, downloadUrl, null, null, null);
    }

    UpdateResult(
            String currentVersion,
            String latestVersion,
            String versionId,
            String versionName,
            String versionType,
            Instant publishedAt,
            String downloadUrl,
            String fileName,
            Long fileSize,
            String changelog
    ) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.versionId = versionId;
        this.versionName = versionName;
        this.versionType = versionType;
        this.publishedAt = publishedAt;
        this.downloadUrl = downloadUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.changelog = changelog;
    }

    /**
     * Builds a result from a Modrinth version object.
     *
     * @param version the newest version object of the project, already sorted
     *                to the front of the version list by {@link ModrinthApi}
     * @param currentVersion the version the caller is currently running
     * @param includeChangelog whether the changelog field carries a value;
     *                         when {@code false} it is reported as empty
     * @return a result describing the newest release
     * @throws ModrinthException if the version object is missing required
     *         members ({@code version_number}, {@code id})
     */
    static UpdateResult from(JsonObject version, String currentVersion, boolean includeChangelog) {
        Objects.requireNonNull(version, "version");

        String latestNumber = requireString(version, "version_number");
        String id = requireString(version, "id");
        JsonObject file = primaryFile(version);

        return new UpdateResult(
                currentVersion,
                latestNumber,
                id,
                optionalString(version, "name"),
                optionalString(version, "version_type"),
                optionalInstant(version, "date_published"),
                optionalString(file, "url"),
                optionalString(file, "filename"),
                optionalLong(file, "size"),
                includeChangelog ? optionalString(version, "changelog") : null
        );
    }

    /**
     * The version the caller is currently running, exactly as configured via
     * {@link ModrinthUpdateChecker.Builder#currentVersion(String)}.
     *
     * @return the configured current version, never {@code null}
     */
    public String currentVersion() {
        return currentVersion;
    }

    /**
     * The version number of the newest listed release.
     *
     * @return e.g. {@code 1.2.1}, never {@code null}
     */
    public String latestVersion() {
        return latestVersion;
    }

    /**
     * The Modrinth-internal ID of the newest release, e.g. as used in
     * {@code https://modrinth.com/project/<project>/version/<id>}.
     *
     * @return the internal version ID, never {@code null}
     */
    public String versionId() {
        return versionId;
    }

    /**
     * The display name the author gave the release.
     *
     * @return e.g. {@code Example Mod 1.2.1}, empty when unset
     */
    public Optional<String> versionName() {
        return Optional.ofNullable(versionName);
    }

    /**
     * The release channel of the newest release.
     *
     * @return {@code "release"}, {@code "beta"} or {@code "alpha"};
     *         empty when unset
     */
    public Optional<String> versionType() {
        return Optional.ofNullable(versionType);
    }

    /**
     * When the newest release was published.
     *
     * @return the publication timestamp; empty when unset or unparseable
     */
    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    /**
     * URL of the primary file, or of the first file when none is marked
     * primary.
     *
     * @return a direct download link that can be handed to a downloader;
     *         empty when the release has no files
     */
    public Optional<String> downloadUrl() {
        return Optional.ofNullable(downloadUrl);
    }

    /**
     * File name of the primary file.
     *
     * @return e.g. {@code example-mod-1.2.1.jar}; empty when unset
     */
    public Optional<String> fileName() {
        return Optional.ofNullable(fileName);
    }

    /**
     * Size of the primary file.
     *
     * @return size in bytes; empty when unset
     */
    public OptionalLong fileSize() {
        return fileSize == null ? OptionalLong.empty() : OptionalLong.of(fileSize);
    }

    /**
     * Markdown changelog of the newest release.
     *
     * @return the changelog; empty when the builder was not configured with
     *         {@link ModrinthUpdateChecker.Builder#includeChangelog()} or the
     *         release has none
     */
    public Optional<String> changelog() {
        return Optional.ofNullable(changelog);
    }

    /**
     * Whether an update is available: this is true when the latest Modrinth
     * release is strictly newer than {@link #currentVersion()}, using a
     * lenient Semantic-Versioning-style comparison (a leading "v" is
     * ignored, {@code 1.10} sorts above {@code 1.9}, pre-releases of the same
     * core version sort below the release). A dev build that is newer than
     * anything on Modrinth does <em>not</em> report an update.
     *
     * @return {@code true} if {@link #currentVersion()} is older than
     *         {@link #latestVersion()}
     */
    public boolean isUpdateAvailable() {
        if (currentVersion == null || latestVersion == null) {
            return !Objects.equals(currentVersion, latestVersion);
        }
        return Versions.isOlder(currentVersion, latestVersion);
    }

    @Override
    public String toString() {
        return "UpdateResult[latestVersion=" + latestVersion
                + ", updateAvailable=" + isUpdateAvailable()
                + ", downloadUrl=" + downloadUrl() + "]";
    }

    // ------------------------------------------------------------------ //
    // parsing helpers
    // ------------------------------------------------------------------ //

    private static JsonObject primaryFile(JsonObject version) {
        if (!version.has("files") || !version.get("files").isJsonArray()) {
            return null;
        }
        JsonArray files = version.getAsJsonArray("files");

        JsonObject first = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            if (first == null) {
                first = file;
            }
            JsonElement primary = file.get("primary");
            if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) {
                return file;
            }
        }
        return first; // files exist but none is marked primary
    }

    private static String requireString(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            throw new ModrinthException(
                    "Modrinth returned malformed version data: missing '" + key + "'.");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
    }

    private static Long optionalLong(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant optionalInstant(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
