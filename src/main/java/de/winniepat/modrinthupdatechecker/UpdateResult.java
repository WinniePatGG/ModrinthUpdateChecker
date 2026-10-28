package de.winniepat.modrinthupdatechecker;

import java.util.Optional;

public final class UpdateResult {

    private final String currentVersion;
    private final String latestVersion;
    private final String versionId;
    private final String downloadUrl;

    public UpdateResult(
            String currentVersion,
            String latestVersion,
            String versionId,
            String downloadUrl
    ) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.versionId = versionId;
        this.downloadUrl = downloadUrl;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String latestVersion() {
        return latestVersion;
    }

    public String versionId() {
        return versionId;
    }

    public Optional<String> downloadUrl() {
        return Optional.ofNullable(downloadUrl);
    }

    public boolean isUpdateAvailable() {
        return !currentVersion.equals(latestVersion);
    }
}