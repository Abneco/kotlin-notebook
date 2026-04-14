plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDirs("resources", "resources-en")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    intellijPlatform {
        // jupyter/core, jupyter/psi, jupyter/execution
        bundledPlugin("intellij.jupyter")
        // intellij.charts platform module
        bundledModule("intellij.charts")
    }

    implementation(projects.core)

    // Bundled third-party libs (shipped with the plugin)
    implementation(libs.lets.plot.export.shadowed)
    implementation(libs.lets.plot.kotlin.json)

    compileOnly(libs.kotlinx.serialization.core)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
