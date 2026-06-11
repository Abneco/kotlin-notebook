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
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
        bundledPlugin("com.jetbrains.performancePlugin")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
