pluginManagement {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "OpenKick"
include(":app")
