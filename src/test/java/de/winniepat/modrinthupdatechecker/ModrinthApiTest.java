package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthApiException;
import de.winniepat.modrinthupdatechecker.exceptions.ModrinthException;
import de.winniepat.modrinthupdatechecker.exceptions.ProjectNotFoundException;
import de.winniepat.modrinthupdatechecker.exceptions.RateLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link ModrinthApi} request building and response handling
 * against a local HTTP server, so no Modrinth traffic happens in tests.
 */
class ModrinthApiTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ModrinthApi api() throws IOException {
        return new ModrinthApi("test-agent/1.0", baseUrl(), CONNECT_TIMEOUT, REQUEST_TIMEOUT);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Starts a server answering /project/{id}/version with the given status and body. */
    private void startServer(int status, String body) throws IOException {
        respond(exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void respond(ModrinthApiTest.ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @Test
    void buildsUrlWithRequestedFilters() throws Exception {
        startServer(200, "[{\"id\":\"v1\",\"version_number\":\"1.0.0\",\"status\":\"listed\"}]");
        ModrinthApi api = api();

        api.getVersions("test-project", "fabric", "1.21.4", false);

        assertEquals(
                "include_changelog=false&loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.4%22%5D",
                rawQuery.get());
    }

    @Test
    void buildsUrlWithoutOptionalFilters() throws Exception {
        startServer(200, "[{\"id\":\"v1\",\"version_number\":\"1.0.0\",\"status\":\"listed\"}]");
        ModrinthApi api = api();

        api.getVersions("test-project", null, null, true);

        assertEquals("include_changelog=true", rawQuery.get());
    }

    @Test
    void filtersUnlistedVersionsAndSortsNewestFirst() throws Exception {
        startServer(200, """
                [
                  { "id": "v1", "version_number": "1.0.0", "date_published": "2026-01-01T00:00:00Z", "status": "listed" },
                  { "id": "v2", "version_number": "1.1.0", "date_published": "2026-01-02T00:00:00Z", "status": "listed" },
                  { "id": "v3", "version_number": "1.2.0", "date_published": "2026-01-03T00:00:00Z", "status": "unlisted" }
                ]
                """);
        ModrinthApi api = api();

        List<JsonObject> versions = api.getVersions("p", null, null, false);

        assertEquals(2, versions.size());
        assertEquals("v2", versions.get(0).get("id").getAsString());
        assertEquals("v1", versions.get(1).get("id").getAsString());
    }

    @Test
    void maps404ToProjectNotFound() throws Exception {
        startServer(404, "{}");
        ModrinthApi api = api();

        ModrinthApiException thrown = assertThrows(
                ProjectNotFoundException.class,
                () -> api.getVersions("missing-project", null, null, false));
        assertEquals(404, thrown.statusCode());
    }

    @Test
    void maps429ToRateLimitWithRetryAfter() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "12");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        ModrinthApi api = api();

        RateLimitException thrown = assertThrows(
                RateLimitException.class,
                () -> api.getVersions("p", null, null, false));
        assertEquals(429, thrown.statusCode());
        assertEquals(12, thrown.retryAfterSeconds());
    }

    @Test
    void maps429WithoutRetryAfterHeader() throws Exception {
        startServer(429, "{}");
        ModrinthApi api = api();

        RateLimitException thrown = assertThrows(
                RateLimitException.class,
                () -> api.getVersions("p", null, null, false));
        assertEquals(-1, thrown.retryAfterSeconds());
    }

    @Test
    void mapsOtherStatusesToApiException() throws Exception {
        startServer(500, "{\"description\":\"boom\"}");
        ModrinthApi api = api();

        ModrinthApiException thrown = assertThrows(
                ModrinthApiException.class,
                () -> api.getVersions("p", null, null, false));
        assertEquals(500, thrown.statusCode());
    }

    @Test
    void rejectsNonArrayResponses() throws Exception {
        startServer(200, "{\"error\":\"not_found\"}");
        ModrinthApi api = api();

        assertThrows(ModrinthException.class,
                () -> api.getVersions("p", null, null, false));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        startServer(200, "{not json");
        ModrinthApi api = api();

        assertThrows(ModrinthException.class,
                () -> api.getVersions("p", null, null, false));
    }
}
