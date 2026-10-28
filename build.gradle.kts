import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("java")
    id("maven-publish")
}

group = "de.winniepat"
version = "0.0.1"

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifactId = "modrinthupdatechecker"

            pom {
                name.set("ModrinthUpdateChecker")
                description.set(
                    "A small, dependency-free HTTP client for Java 21 built on the JDK's HttpClient."
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