package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonObject;
import de.winniepat.modrinthupdatechecker.exceptions.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks a Modrinth project for updates against the Modrinth REST API.
 *
 * <p>Create an instance once and reuse it for repeated checks. Each check
 * creates one HTTP request; instances own their HTTP client, which is
 * released in {@link #close()} — call it from your plugin's shutdown path or
 * use try-with-resources.
 *
 * <p>All checks are asynchronous and return a {@link CompletableFuture}; the
 * default execution pool is {@link ForkJoinPool#commonPool()}, which can be
 * replaced with {@link #checkForUpdates(Executor)} (e.g. your server's async
 * scheduler). Failures complete the future exceptionally as unchecked
 * {@link ModrinthException} subtypes ({@link ProjectNotFoundException},
 * {@link RateLimitException}, {@link NoCompatibleVersionException},
 * {@link ModrinthApiException}, or a {@link ModrinthException} wrapping an
 * {@link IOException}); use {@code exceptionally} or {@code whenComplete} to
 * consume them. Interruption completes the future with a
 * {@link CompletionException} whose cause is the {@link InterruptedException};
 * the thread's interrupt flag is restored first.
 */
public final class ModrinthUpdateChecker implements AutoCloseable {

    private static final String LIBRARY_VERSION = libraryVersion();

    /**
     * Modrinth asks clients to identify themselves via User-Agent. The
     * default carries the product, its build-time version and the project
     * URL as contact information; see {@link Builder#userAgent(String)} to
     * override it.
     */
    private static final String DEFAULT_USER_AGENT =
            "WinniePatGG/ModrinthUpdateChecker/" + LIBRARY_VERSION
                    + " (+https://github.com/WinniePatGG/ModrinthUpdateChecker)";

    private final String projectId;
    private final String currentVersion;
    private final String loader;
    private final String gameVersion;
    private final boolean includeChangelog;
    private final ModrinthApi api;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ModrinthUpdateChecker(Builder builder) {
        this.projectId = builder.projectId;
        this.currentVersion = builder.currentVersion;
        this.loader = builder.loader == null || builder.loader.isBlank() ? null : builder.loader;
        this.gameVersion =
                builder.gameVersion == null || builder.gameVersion.isBlank() ? null : builder.gameVersion;
        this.includeChangelog = builder.includeChangelog;
        this.api = new ModrinthApi(
                builder.userAgent,
                builder.baseUrl,
                builder.connectTimeout,
                builder.requestTimeout);
    }

    /**
     * Creates a new builder for a checker instance.
     *
     * @return a fresh builder with this library's default User-Agent and timeouts
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks Modrinth for the newest version of the configured project that
     * is compatible with the configured loader and game version filters,
     * running on {@link ForkJoinPool#commonPool()}.
     *
     * @return a future completing with the newest release, or completing
     *         exceptionally with a {@link ModrinthException} subtype (see
     *         the class documentation)
     */
    public CompletableFuture<UpdateResult> checkForUpdates() {
        return checkForUpdates(ForkJoinPool.commonPool());
    }

    /**
     * As {@link #checkForUpdates()}, but with explicit control over the
     * executor the HTTP request and response parsing run on.
     *
     * @param executor executes the check; e.g. your server's async scheduler
     * @return a future completing with the newest release, or completing
     *         exceptionally with a {@link ModrinthException} subtype (see
     *         the class documentation)
     * @throws NullPointerException if {@code executor} is {@code null}
     * @throws IllegalStateException if this checker is already closed
     */
    public CompletableFuture<UpdateResult> checkForUpdates(Executor executor) {
        Objects.requireNonNull(executor, "executor");

        if (closed.get()) {
            CompletableFuture<UpdateResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                    new IllegalStateException("ModrinthUpdateChecker is closed."));
            return failed;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<JsonObject> versions =
                        api.getVersions(projectId, loader, gameVersion, includeChangelog);
                if (versions.isEmpty()) {
                    throw new NoCompatibleVersionException(noCompatibleMessage());
                }
                return UpdateResult.from(versions.get(0), currentVersion, includeChangelog);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, executor);
    }

    private String noCompatibleMessage() {
        StringBuilder message = new StringBuilder()
                .append("Modrinth project '").append(projectId)
                .append("' has no listed versions");
        if (loader != null) {
            message.append(" for loader '").append(loader).append('\'');
        }
        if (gameVersion != null) {
            message.append(" for game version '").append(gameVersion).append('\'');
        }
        return message.append('.').toString();
    }

    /**
     * Releases the HTTP client owned by this checker. The call blocks until
     * in-flight requests finish and never throws. After closing, further
     * {@link #checkForUpdates()} calls complete exceptionally with an
     * {@link IllegalStateException}.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                api.close();
            } catch (IOException ignored) {
                // Best-effort shutdown; HttpClient#close rarely fails here.
            }
        }
    }

    /**
     * Reads the library version that Gradle stamped into
     * {@code version.properties} at build time, keeping the default
     * User-Agent in sync with the published artifact.
     */
    private static String libraryVersion() {
        try (InputStream resource = ModrinthUpdateChecker.class.getResourceAsStream("/version.properties")) {
            if (resource != null) {
                Properties properties = new Properties();
                properties.load(resource);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.strip();
                }
            }
        } catch (IOException ignored) {
            // Fall through to the manifest / unknown fallbacks.
        }
        Package pkg = ModrinthUpdateChecker.class.getPackage();
        if (pkg != null) {
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isBlank()) {
                return version;
            }
        }
        return "unknown";
    }

    /**
     * Fluent builder for {@link ModrinthUpdateChecker} instances.
     *
     * <p>Required settings: {@link #projectId(String)} and
     * {@link #currentVersion(String)}; {@link #build()} validates the rest.
     */
    public static final class Builder {

        private String projectId;
        private String currentVersion;
        private String loader;
        private String gameVersion;
        private boolean includeChangelog;
        private String userAgent = DEFAULT_USER_AGENT;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(20);

        // Overridden in tests only.
        private String baseUrl = ModrinthApi.DEFAULT_BASE_URL;

        /**
         * Creates a builder; use {@link ModrinthUpdateChecker#builder()}
         * instead of instantiating directly.
         */
        private Builder() {
        }

        /** Package-private test hook: points the client at another base URL. */
        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the target project.
         *
         * @param projectId the Modrinth project ID <em>or slug</em>, both are
         *                  accepted by the API — the slug is the readable part
         *                  of the project URL, e.g. {@code example-mod} in
         *                  {@code https://modrinth.com/mod/example-mod}
         * @return this builder
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the version your software is currently running.
         *
         * @param currentVersion the running version exactly as announced, e.g.
         *                       your plugin's {@code plugin.yml} {@code version}
         * @return this builder
         */
        public Builder currentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        /**
         * Sets an optional loader filter ({@code paper}, {@code spigot},
         * {@code fabric}, ...).
         *
         * @param loader the loader; only versions marked by their author for
         *               this loader are considered. Leave unset to consider
         *               all loaders.
         * @return this builder
         */
        public Builder loader(String loader) {
            this.loader = loader;
            return this;
        }

        /**
         * Sets an optional game version filter.
         *
         * @param gameVersion the game version ({@code 1.21.4}, ...); only
         *                    versions marked by their author for this game
         *                    version are considered. Leave unset to consider
         *                    all game versions.
         * @return this builder
         */
        public Builder gameVersion(String gameVersion) {
            this.gameVersion = gameVersion;
            return this;
        }

        /**
         * Requests the changelog so {@link UpdateResult#changelog()} is
         * populated.
         *
         * @return this builder
         */
        public Builder includeChangelog() {
            this.includeChangelog = true;
            return this;
        }

        /**
         * Overrides the User-Agent sent to Modrinth.
         *
         * @param userAgent a descriptive User-Agent identifying your client,
         *                  e.g. {@code myplugin/1.0.0 (+https://your.site/repo)};
         *                  the default already contains this library's identity
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Sets the connection-establishment timeout.
         *
         * @param connectTimeout how long connecting may take, default 10 seconds
         * @return this builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the per-request response timeout.
         *
         * @param requestTimeout how long a single HTTP request may take,
         *                       default 20 seconds
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Validates the configuration and builds the checker.
         *
         * @return a ready-to-use checker instance
         * @throws IllegalStateException if required values are missing or a
         *         timeout is not positive
         */
        public ModrinthUpdateChecker build() {
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalStateException("A Modrinth project ID is required.");
            }
            if (currentVersion == null || currentVersion.isBlank()) {
                throw new IllegalStateException("The current version is required.");
            }
            if (userAgent == null || userAgent.isBlank()) {
                throw new IllegalStateException("A User-Agent is required.");
            }
            if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
                throw new IllegalStateException("The connect timeout must be positive.");
            }
            if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalStateException("The request timeout must be positive.");
            }
            return new ModrinthUpdateChecker(this);
        }
    }
}
