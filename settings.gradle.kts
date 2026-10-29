rootProject.name = "ModrinthUpdateChecker"

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername").get()
        password = providers.gradleProperty("mavenCentralPassword").get()

        publishingType = "USER_MANAGED"
    }
}