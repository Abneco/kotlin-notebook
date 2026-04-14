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
        // Provides K2 / FIR frontend APIs, scripting k2
        bundledPlugin("org.jetbrains.kotlin")
        // jupyter/psi, jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(project(":core"))
}
