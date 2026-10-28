package de.winniepat.modrinthupdatechecker;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ModrinthUpdateChecker {

    private final String projectId;
    private final String currentVersion;
    private final String loader;
    private final String gameVersion;
    private final ModrinthApi api;

    private ModrinthUpdateChecker(Builder builder) {
        this.projectId = builder.projectId;
        this.currentVersion = builder.currentVersion;
        this.loader = builder.loader;
        this.gameVersion = builder.gameVersion;
        this.api = new ModrinthApi(builder.userAgent);
    }

    public static Builder builder() {
        return new Builder();
    }

    public CompletableFuture<UpdateResult> checkForUpdated() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<JsonObject> versions = api.getVersions(projectId, loader, gameVersion);

                if (versions.isEmpty()) throw new IOException("No compatible listed versions found.");

                JsonObject latest = versions.get(0);

                String latestVersion = latest.get("version_number").getAsString();
                String versionId = latest.get("id").getAsString();
                String downloadUrl = api.getPrimaryDownloadUrl(latest).orElse(null);

                return new UpdateResult(currentVersion, latestVersion, versionId, downloadUrl);
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        });
    }

    public static final class Builder {
        private String projectId;
        private String currentVersion;
        private String loader;
        private String gameVersion;

        private String userAgent = "WinniePatGG/ModrinthUpdateChecker/0.0.1";

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder currentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        public Builder loader(String loader) {
            this.loader = loader;
            return this;
        }

        public Builder gameVersion(String gameVersion) {
            this.gameVersion = gameVersion;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

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

            return new ModrinthUpdateChecker(this);
        }
    }
}