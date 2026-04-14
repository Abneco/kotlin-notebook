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
        // jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.intellij.performanceTesting")
    }

    implementation(project(":core"))

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
