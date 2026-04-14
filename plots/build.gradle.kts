plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellijPlatformModule)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // jupyter/core, jupyter/psi, jupyter/execution
        bundledPlugin("intellij.jupyter")
        // intellij.charts platform module
        bundledModule("intellij.charts")
    }

    implementation(project(":core"))

    // Bundled third-party libs (shipped with the plugin)
    implementation(libs.lets.plot.export.shadowed)
    implementation(libs.lets.plot.kotlin.json)

    compileOnly(libs.kotlinx.serialization.core)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
