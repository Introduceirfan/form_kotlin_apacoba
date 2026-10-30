// Project Android untuk portal FORM. Dibuka terpisah dari Laravel:
// di Android Studio pilih folder `android/`, bukan root repo.
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

rootProject.name = "FormApp"
include(":app")
