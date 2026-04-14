plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir("resources")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    intellijPlatform {
        // jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.jetbrains.performancePlugin")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
