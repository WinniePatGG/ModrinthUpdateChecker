rootProject.name = "ModrinthUpdateChecker"

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

nmcpSettings {
    centralPortal {
        // Empty defaults keep plain builds working without publishing
        // credentials; the publish task itself guards against missing
        // properties and fails with a clear message.
        username = providers.gradleProperty("mavenCentralUsername").orElse("")
        password = providers.gradleProperty("mavenCentralPassword").orElse("")

        publishingType = "USER_MANAGED"
    }
}