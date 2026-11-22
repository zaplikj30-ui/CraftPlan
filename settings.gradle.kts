pluginManagement {
    repositories {
        System.getenv("CRAFTPLAN_MAVEN_CACHE")?.let { maven { url = uri(it) } }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        System.getenv("CRAFTPLAN_MAVEN_CACHE")?.let { maven { url = uri(it) } }
        google()
        mavenCentral()
    }
}

rootProject.name = "CraftPlan"
include(":app")
