package de.winniepat.modrinthupdatechecker;

import com.sun.net.httpserver.HttpServer;
import de.winniepat.modrinthupdatechecker.exceptions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link ModrinthUpdateChecker} against a local HTTP
 * server that stands in for the Modrinth API.
 */
class ModrinthUpdateCheckerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
    }

    private ModrinthUpdateChecker.Builder freshBuilder() {
        return ModrinthUpdateChecker.builder()
                .projectId("test-project")
                .currentVersion("1.0.0")
                .connectTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(5));
    }

    /** A builder pointed at the local test server; requires {@link #startServer}. */
    private ModrinthUpdateChecker.Builder testBuilder() {
        return freshBuilder().baseUrl(baseUrl());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void returnsNewestVersionResult() throws Exception {
        startServer(exchange -> {
            respond(exchange, 200, """
                    [
                      { "id": "old", "version_number": "1.0.0", "date_published": "2026-01-01T00:00:00Z", "status": "listed" },
                      { "id": "new", "version_number": "1.1.0", "date_published": "2026-01-02T00:00:00Z", "status": "listed" }
                    ]
                    """);
        });
        ModrinthUpdateChecker checker = testBuilder().build();

        UpdateResult result = checker.checkForUpdates().join();

        assertTrue(result.isUpdateAvailable());
        assertEquals("1.1.0", result.latestVersion());
        assertEquals("new", result.versionId());
        assertEquals("1.0.0", result.currentVersion());
        checker.close();
    }

    @Test
    void reportsNoUpdateWhenRunningLatest() throws Exception {
        startServer(exchange -> respond(exchange, 200,
                "[{ \"id\": \"v1\", \"version_number\": \"1.0.0\", \"date_published\": \"2026-01-01T00:00:00Z\" }]"));
        ModrinthUpdateChecker checker = testBuilder().currentVersion("v1.0.0").build();

        UpdateResult result = checker.checkForUpdates().join();

        assertFalse(result.isUpdateAvailable());
        checker.close();
    }

    @Test
    void runsSynchronouslyWhenGivenDirectExecutor() throws Exception {
        startServer(exchange -> respond(exchange, 200,
                "[{ \"id\": \"v1\", \"version_number\": \"1.0.0\", \"date_published\": \"2026-01-01T00:00:00Z\" }]"));
        ModrinthUpdateChecker checker = testBuilder().currentVersion("0.9").build();

        UpdateResult result = checker.checkForUpdates(Runnable::run).join();

        assertTrue(result.isUpdateAvailable());
        checker.close();
    }

    @Test
    void includesChangelogWhenRequested() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        startServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200,
                    "[{ \"id\": \"v1\", \"version_number\": \"1.0.0\", \"date_published\": \"2026-01-01T00:00:00Z\", \"changelog\": \"Welcome!\" }]");
        });
        ModrinthUpdateChecker checker = testBuilder().includeChangelog().build();

        UpdateResult result = checker.checkForUpdates().join();

        assertTrue(query.get().contains("include_changelog=true"));
        assertEquals("Welcome!", result.changelog().orElseThrow());
        checker.close();
    }

    @Test
    void rejectsAfterClose() throws Exception {
        startServer(exchange -> respond(exchange, 200, "[]"));
        ModrinthUpdateChecker checker = testBuilder().build();
        checker.close();
        checker.close(); // idempotent

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof IllegalStateException);
    }

    @Test
    void maps404ToProjectNotFoundException() throws Exception {
        startServer(exchange -> respond(exchange, 404, "{}"));
        ModrinthUpdateChecker checker = testBuilder().build();

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof ProjectNotFoundException);
        checker.close();
    }

    @Test
    void mapsEmptyVersionListToNoCompatibleVersionException() throws Exception {
        startServer(exchange -> respond(exchange, 200, "[]"));
        ModrinthUpdateChecker checker = testBuilder().build();

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof NoCompatibleVersionException);
        assertTrue(cause.getMessage().contains("test-project"));
        checker.close();
    }

    @Test
    void mapsRateLimitToRateLimitException() throws Exception {
        startServer(exchange -> respond(exchange, 429, "{}"));
        ModrinthUpdateChecker checker = testBuilder().build();

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof RateLimitException);
        checker.close();
    }

    @Test
    void mapsServerErrorsToModrinthApiException() throws Exception {
        startServer(exchange -> respond(exchange, 503, "{}"));
        ModrinthUpdateChecker checker = testBuilder().build();

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof ModrinthApiException);
        assertEquals(503, ((ModrinthApiException) cause).statusCode());
        checker.close();
    }

    @Test
    void mapsMalformedResponsesToModrinthException() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"oops\":true}"));
        ModrinthUpdateChecker checker = testBuilder().build();

        Throwable cause = failureCauseOf(checker);

        assertTrue(cause instanceof ModrinthException);
        assertFalse(cause instanceof ModrinthApiException);
        checker.close();
    }

    private static Throwable failureCauseOf(ModrinthUpdateChecker checker) {
        CompletableFuture<UpdateResult> future = checker.checkForUpdates();
        try {
            future.join();
        } catch (CompletionException e) {
            return e.getCause();
        }
        throw new AssertionError("expected the update check to fail");
    }

    @Test
    void builderRejectsMissingProjectIdAndVersion() {
        assertThrows(IllegalStateException.class,
                () -> ModrinthUpdateChecker.builder().build());
        assertThrows(IllegalStateException.class,
                () -> ModrinthUpdateChecker.builder().projectId("p").build());
    }

    @Test
    void builderRejectsNonPositiveTimeouts() {
        assertThrows(IllegalStateException.class, () -> freshBuilder()
                .baseUrl("http://unused.invalid")
                .connectTimeout(Duration.ZERO)
                .build());
        assertThrows(IllegalStateException.class, () -> freshBuilder()
                .baseUrl("http://unused.invalid")
                .requestTimeout(Duration.ofSeconds(-1))
                .build());
    }

    @Test
    void builderRejectsNullExecutor() {
        assertThrows(NullPointerException.class,
                () -> freshBuilder().baseUrl("http://unused.invalid").build().checkForUpdates(null));
    }
}
