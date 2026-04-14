plugins {
    alias(libs.plugins.kotlin.jvm)
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
        bundledPlugin("org.jetbrains.kotlin")
        // intellij.jupyter plugin: jupyter/psi, jupyter/convert, jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.core)
}
