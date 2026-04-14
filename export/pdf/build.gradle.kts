plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellijPlatformModule)
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
        bundledPlugin("org.jetbrains.kotlin")
        // intellij.jupyter plugin: jupyter/psi, jupyter/convert, jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(project(":core"))
}
