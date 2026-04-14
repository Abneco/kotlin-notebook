rootProject.name = "kotlin-notebook"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Version is catalogued in gradle/libs.versions.toml [foojayResolver].
    // alias() is unavailable in settings plugin blocks due to a Kotlin DSL scope limitation.
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
    // ":tests:unitTests",  // Requires JetBrains-internal test infrastructure (JupyterBaseTestCase, IdeaTestUtil, etc.) — not publicly available
    // ":tests:perfTests",  // Requires JetBrains-internal ide-starter-extended — not publicly available
)
