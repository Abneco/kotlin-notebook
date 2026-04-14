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
        // Gradle bundled plugin
        bundledPlugin("org.jetbrains.plugins.gradle")
    }

    implementation(project(":core"))
}
