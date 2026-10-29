# Releasing ModrinthUpdateChecker

This guide explains how to release a new version of **ModrinthUpdateChecker** to GitHub, Reposilite, GitHub Packages, and Maven Central.

## Release checklist

For this example, the version being released is `0.0.2`.

### 1. Update the version

Open `build.gradle.kts` and change:

```kotlin
version = "0.0.1"
```

to:

```kotlin
version = "0.0.2"
```

Update the README and changelog if necessary.

Check that the Maven POM metadata is correct, including the project description, license, developer information, and repository URLs.

### 2. Build and test

Open PowerShell in the project directory:

```powershell
cd G:\Coding\ModrinthUpdateChecker
```

Run a clean build:

```powershell
.\gradlew clean build
```

Verify that the build finishes with `BUILD SUCCESSFUL`.

Test the signing configuration:

```powershell
.\gradlew signMavenJavaPublication
```

Do not continue if either command fails.

### 3. Push the source code to GitHub

Check your working tree:

```powershell
git status
```

Commit and push the release changes:

```powershell
git add .
git commit -m "Release 0.0.2"
git push origin main
```

If your default branch is not `main`, replace it with the correct branch name.

### 4. Publish to Reposilite

Publish the Maven artifact:

```powershell
.\gradlew publishMavenJavaPublicationToReposiliteRepository
```

Gradle uses the credentials configured in your user-level `gradle.properties` file:

```properties
reposiliteUsername=YOUR_USERNAME
reposilitePassword=YOUR_PASSWORD
```

Verify that version `0.0.2` appears in your Reposilite repository.

### 5. Publish to GitHub Packages

If you use GitHub Packages as a Maven repository, run:

```powershell
.\gradlew publishMavenJavaPublicationToGitHubPackagesRepository
```

Ensure your user-level `gradle.properties` contains the required credentials:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

The GitHub token must have the required package publishing permissions.

Verify the package in your GitHub repository.

**Note:** GitHub Releases and GitHub Packages are separate. GitHub Packages hosts Maven artifacts; GitHub Releases provides a download page for tagged versions.

### 6. Upload to Maven Central

Publish the deployment through the NMCP plugin:

```powershell
.\gradlew nmcpPublishAggregationToCentralPortal
```

This project uses `USER_MANAGED` publishing, so the deployment must be reviewed and released manually.

Open the [Sonatype Central Portal](https://central.sonatype.com/) and:

1. Open the newly uploaded deployment.
2. Wait for validation to finish.
3. Review and fix any validation errors.
4. Publish the deployment once validation succeeds.

Wait for publication to complete before considering the artifact available to consumers.

### 7. Create a GitHub Release

Create a version tag:

```powershell
git tag v0.0.2
git push origin v0.0.2
```

Open the [GitHub Releases page](https://github.com/WinniePatGG/ModrinthUpdateChecker/releases/new).

1. Select the `v0.0.2` tag.
2. Set the release title to `ModrinthUpdateChecker 0.0.2`.
3. Add a changelog describing the changes.
4. Optionally attach the built JAR and sources JAR from `build/libs`.
5. Publish the release.

### 8. Verify the published dependency

After Maven Central has published the artifact and it has propagated, consumers can add the dependency without credentials.

For a Groovy Gradle project:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'de.winniepat:modrinthupdatechecker:0.0.2'
}
```

For a Kotlin DSL Gradle project:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.winniepat:modrinthupdatechecker:0.0.2")
}
```

Verify that the dependency resolves successfully in a separate project.

## Important notes

- Keep publishing credentials and private signing keys out of source control.
- Publish each version only once to each repository.
- If a deployment fails, inspect its status before retrying.
- Never overwrite an already released version. Use a new version for fixes.
- Maven Central may take time to propagate a newly released artifact.
- GitHub Releases, GitHub Packages, Reposilite, and Maven Central are independent destinations; publishing to one does not automatically publish to the others.
