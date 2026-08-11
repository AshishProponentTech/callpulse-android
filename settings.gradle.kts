pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "callpulse-android"

// Module layout per CALLPULSE_BUILD_INSTRUCTIONS §10.
include(":app")
include(":core-common")
include(":core-network")
include(":core-data")
include(":core-ui")
include(":feature-auth")
include(":feature-capture")
include(":feature-review")
include(":feature-day")
