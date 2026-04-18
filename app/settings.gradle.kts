pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ArchitectAI"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:svg")
include(":feature:chat")
include(":feature:build")
include(":feature:library")
include(":feature:gallery")
