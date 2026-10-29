import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.util.Base64

plugins {
    id("java")
    id("maven-publish")
    id("signing")
}

group = "de.winniepat"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val versionProps = mapOf("version" to project.version.toString())
    inputs.properties(versionProps)
    filesMatching("version.properties") {
        expand(versionProps)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifactId = "modrinthupdatechecker"

            pom {
                name.set("ModrinthUpdateChecker")
                description.set(
                    "A lightweight Modrinth update checker for Java 21."
                )
                url.set("https://github.com/WinniePatGG/ModrinthUpdateChecker")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("WinniePatGG")
                        name.set("Patrick Schuler")
                    }
                }

                scm {
                    url.set("https://github.com/WinniePatGG/ModrinthUpdateChecker")
                    connection.set("scm:git:https://github.com/WinniePatGG/ModrinthUpdateChecker.git")
                    developerConnection.set("scm:git:https://github.com/WinniePatGG/ModrinthUpdateChecker.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "reposilite"
            url = uri("https://maven.winniepat.de/releases")

            credentials {
                username = providers.gradleProperty("reposiliteUsername").get()
                password = providers.gradleProperty("reposilitePassword").get()
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/WinniePatGG/ModrinthUpdateChecker")

            credentials {
                username = providers.gradleProperty("gpr.user").orElse("").get()
                password = providers.gradleProperty("gpr.key").orElse("").get()
            }
        }
    }
}

val requiredCredentials = mapOf(
    "publishMavenJavaPublicationToReposiliteRepository" to
            listOf("reposiliteUsername", "reposilitePassword"),
    "publishMavenJavaPublicationToGitHubPackagesRepository" to
            listOf("gpr.user", "gpr.key"),
)

tasks.withType<PublishToMavenRepository>().configureEach {
    val required = requiredCredentials[this.name] ?: emptyList()
    doFirst {
        val missing = required.filterNot { providers.gradleProperty(it).isPresent }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Cannot publish to $name: missing Gradle properties ${missing.joinToString()}. " +
                        "Add them to gradle.properties (gitignored) or pass -P<key>=<value>."
            )
        }
    }
}

// The Central Portal aggregation task is created by the NMCP settings plugin
// and is not a PublishToMavenRepository, so give it the same fail-early guard
// instead of surfacing a cryptic authentication error after other
// repositories were already published.
tasks.matching { it.name == "nmcpPublishAggregationToCentralPortal" }.configureEach {
    doFirst {
        val missing = listOf("mavenCentralUsername", "mavenCentralPassword")
            .filterNot { providers.gradleProperty(it).isPresent }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Cannot publish to the Central Portal: missing Gradle properties ${missing.joinToString()}. " +
                        "Add them to gradle.properties (gitignored) or pass -P<key>=<value>."
            )
        }
    }
}

signing {
    val encodedKey = providers.gradleProperty("signingKey")
    val signingPassword = providers.gradleProperty("signingPassword")

    if (encodedKey.isPresent) {
        val decodedKey = String(
            Base64.getDecoder().decode(encodedKey.get()),
            Charsets.UTF_8
        )

        useInMemoryPgpKeys(
            decodedKey,
            signingPassword.orNull
        )
    }

    sign(publishing.publications["mavenJava"])
}

tasks.register("publishAll") {
    group = "publishing"

    dependsOn(
        "publishMavenJavaPublicationToReposiliteRepository",
        "publishMavenJavaPublicationToGitHubPackagesRepository",
        "nmcpPublishAggregationToCentralPortal"
    )
}