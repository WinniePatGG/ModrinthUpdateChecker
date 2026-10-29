package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthApiException;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthException;
import de.winniepat.modrinthupdatechecker.exceptions.ProjectNotFoundException;
import de.winniepat.modrinthupdatechecker.exceptions.RateLimitException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the parts of the Modrinth REST API this library needs.
 * Package-private; not part of the public API.
 */
final class ModrinthApi {

    /** The base URL of the Modrinth REST API. Overridable in tests only. */
    static final String DEFAULT_BASE_URL = "https://api.modrinth.com/v2";

    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_OK = 200;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final HttpClient client;
    private final String userAgent;
    private final String baseUrl;
    private final Duration requestTimeout;

    ModrinthApi(
            String userAgent,
            String baseUrl,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        this.userAgent = userAgent;
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;

        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * Shuts the underlying HTTP client down, blocking until in-flight
     * requests have finished.
     */
    void close() throws IOException {
        client.close();
    }

    /**
     * Fetches the versions of the given project, filtered by loader and game
     * version (each optional), keeping only listed versions and returning
     * them newest first.
     *
     * <p>Statuses are mapped to exceptions: a 404 becomes a
     * {@link ProjectNotFoundException}, a 429 a {@link RateLimitException},
     * every other non-200 status a {@link ModrinthApiException}. Network
     * failures are reported as {@link ModrinthException} with the underlying
     * {@link IOException} as cause.
     */
    List<JsonObject> getVersions(
            String projectId,
            String loader,
            String gameVersion,
            boolean includeChangelog
    ) throws InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildVersionsUrl(projectId, loader, gameVersion, includeChangelog)))
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ModrinthException("Failed to contact Modrinth: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ModrinthException("Invalid HTTP request to Modrinth: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        if (statusCode == HTTP_NOT_FOUND) {
            throw new ProjectNotFoundException(
                    "No Modrinth project found for '" + projectId + "' (HTTP 404).");
        }
        if (statusCode == HTTP_TOO_MANY_REQUESTS) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            throw new RateLimitException(
                    "Modrinth API rate limit exceeded (HTTP 429).",
                    parseRetryAfterSeconds(retryAfter));
        }
        if (statusCode != HTTP_OK) {
            throw new ModrinthApiException(
                    statusCode, "Modrinth API returned unexpected HTTP " + statusCode + ".");
        }

        return parseVersions(response.body());
    }

    private String buildVersionsUrl(
            String projectId,
            String loader,
            String gameVersion,
            boolean includeChangelog
    ) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/project/")
                .append(encode(projectId))
                .append("/version?include_changelog=")
                .append(includeChangelog);

        if (loader != null) {
            url.append("&loaders=")
                    .append(encode("[\"" + loader + "\"]"));
        }

        if (gameVersion != null) {
            url.append("&game_versions=")
                    .append(encode("[\"" + gameVersion + "\"]"));
        }

        return url.toString();
    }

    /**
     * Parses the response body into listed version objects, sorted by
     * publication date, newest first.
     */
    private List<JsonObject> parseVersions(String body) {
        JsonArray array;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) {
                throw new ModrinthException(
                        "Modrinth API returned an unexpected response (expected a JSON array).");
            }
            array = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            if (e instanceof ModrinthException re) {
                throw re;
            }
            throw new ModrinthException("Modrinth API returned malformed JSON.", e);
        }

        List<JsonObject> versions = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new ModrinthException("Modrinth API returned an unexpected version entry.");
            }
            JsonObject version = element.getAsJsonObject();
            if (isListed(version)) {
                versions.add(version);
            }
        }

        versions.sort((a, b) -> Long.compare(publishedMillis(b), publishedMillis(a)));
        return versions;
    }

    /**
     * Only listed versions count as candidates for an update.
     */
    private static boolean isListed(JsonObject version) {
        JsonElement status = version.get("status");
        if (status == null || !status.isJsonPrimitive()) {
            return true;
        }
        return "listed".equals(status.getAsString());
    }

    /**
     * Publication timestamp, or {@link Long#MIN_VALUE} when absent or
     * unparseable (which sorts as oldest).
     */
    private static long publishedMillis(JsonObject version) {
        JsonElement published = version.get("date_published");
        if (published == null || !published.isJsonPrimitive()) {
            return Long.MIN_VALUE;
        }
        try {
            return OffsetDateTime.parse(published.getAsString()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return Long.MIN_VALUE;
        }
    }

    private static long parseRetryAfterSeconds(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
