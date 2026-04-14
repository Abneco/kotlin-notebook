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
        // jupyter/core, jupyter/psi, dataspell/jupyter/sql/common
        bundledPlugin("intellij.jupyter")
        // dbe/database
        bundledPlugin("com.intellij.database")
        bundledModule("intellij.java.backend")
    }

    implementation(project(":core"))

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
