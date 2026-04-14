plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        kotlin.srcDirs("src", "gen")
        resources.srcDir("resources")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    intellijPlatform {
        // Provides K2 / FIR frontend APIs, scripting k2
        bundledPlugin("org.jetbrains.kotlin")
        // jupyter/psi, jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.core)
}
