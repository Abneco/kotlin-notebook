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
        // jupyter/core, jupyter/tables
        bundledPlugin("intellij.jupyter")
        // notebooks/dataframe, python/scientific-tables
        bundledPlugin("com.intellij.notebooks.core")
    }

    implementation(project(":core"))

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
