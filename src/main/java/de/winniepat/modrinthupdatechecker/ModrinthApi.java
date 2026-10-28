package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ModrinthApi {

    private static final String API_URL =
            "https://api.modrinth.com/v2";

    private final HttpClient client;
    private final String userAgent;

    ModrinthApi(String userAgent) {
        this.userAgent = userAgent;

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    List<JsonObject> getVersions(
            String projectId,
            String loader,
            String gameVersion
    ) throws IOException, InterruptedException {

        StringBuilder url = new StringBuilder(API_URL)
                .append("/project/")
                .append(encode(projectId))
                .append("/version?include_changelog=false");

        if (loader != null) {
            url.append("&loaders=")
                    .append(encode("[\"" + loader + "\"]"));
        }

        if (gameVersion != null) {
            url.append("&game_versions=")
                    .append(encode("[\"" + gameVersion + "\"]"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Modrinth API returned HTTP "
                            + response.statusCode()
            );
        }

        JsonArray array = JsonParser.parseString(
                response.body()
        ).getAsJsonArray();

        List<JsonObject> versions = new ArrayList<>();

        for (JsonElement element : array) {
            JsonObject version = element.getAsJsonObject();

            // Only consider published, listed versions.
            if (version.has("status")
                    && !version.get("status").getAsString()
                    .equals("listed")) {
                continue;
            }

            versions.add(version);
        }

        return versions;
    }

    Optional<String> getPrimaryDownloadUrl(JsonObject version) {
        if (!version.has("files")) {
            return Optional.empty();
        }

        JsonArray files = version.getAsJsonArray("files");

        if (files.isEmpty()) {
            return Optional.empty();
        }

        JsonObject fallback = files.get(0).getAsJsonObject();

        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();

            if (file.has("primary")
                    && file.get("primary").getAsBoolean()) {
                return Optional.of(
                        file.get("url").getAsString()
                );
            }
        }

        return Optional.of(fallback.get("url").getAsString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}