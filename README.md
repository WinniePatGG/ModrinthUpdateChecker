# ModrinthUpdateChecker

A simple update checker for Minecraft plugins/mods using the Modrinth API.

## Getting Started
1. Add the repository to your `build.gradle`:
```gradle
repositories {
    maven { url "https://maven.winniepat.de/releases" } // if you want to use my repository
    mavenCentral() // if you want to use mavenCentral
}
```

2. Add the dependency to your `build.gradle`:
```gradle
dependencies {
    implementation "de.winniepat:modrinthupdatechecker:0.0.1"
}
```

3. Create a `ModrinthUpdateChecker` instance in your plugin's main class:
```java
ModrinthChecker checker = ModrinthUpdateChecker.builder()
        .projectId("YOUR_MODRINTH_PROJECT_ID") // Get your project ID from the Modrinth project page
        .currentVersion(getDescription().getVersion()) // Your plugin's/mod's current version
        .build();
```
![1](img/1.png)

4. In your `onEnable()` method, check for updates:
```java
checker.checkForUpdated().thenAccept(result -> {
    if (result.isUpdateAvailable()) {
        getLogger().warning("A new version of GamblingPlugin is available: " + result.latestVersion());
        result.downloadUrl().ifPresent(url -> getLogger().warning("Download it here: " + url));
    }
});
```
![1](img/2.png)
