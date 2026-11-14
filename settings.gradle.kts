pluginManagement {
    repositories {
        google()
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

rootProject.name = "hey-phone"

// ai.hey:tokens-compose (HeyTokens.kt + fonts, generated in companion-shared) is substituted by this included build.
includeBuild("shared/android")

include(":app")
