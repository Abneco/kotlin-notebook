rootProject.name = "kotlin-notebook"

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

include(
    ":core",
    ":tables",
    ":sql",
    ":plots",
    ":export:pdf",
    ":buildSystems:gradle",
    ":liveTemplates",
    ":k1",
    ":k2",
    ":debug",
    ":debug:renders",
    ":notekit",
    ":performancePlugin",
    ":tests:unitTests",
    ":tests:perfTests",
)
