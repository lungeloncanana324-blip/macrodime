// MacroDime for Android.
//
// Two modules, mirroring the iOS layering:
//   :core  Domain + Engine. Pure Kotlin, no Android imports, so the science and
//          the swap algorithm are tested on a plain JVM in seconds. A port of
//          MacroDime/Domain and MacroDime/Engine, held to the same tests.
//   :app   Persistence (Room), view models and the Compose UI.

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

rootProject.name = "MacroDime"
include(":core", ":app")
