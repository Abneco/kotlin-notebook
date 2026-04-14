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
        // Provides debugger-collections-visualizer modules
        bundledPlugin("com.intellij.debugger.collections.visualizer")
        bundledModule("intellij.java.backend")
    }

    implementation(project(":debug"))

    compileOnly(libs.kotlinx.coroutines.core)
}
