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
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaIdeApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaNonPublicApi",
            "-opt-in=org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi",
            "-opt-in=org.jetbrains.kotlin.K1Deprecation",
        )
    }
}

dependencies {
    intellijPlatform {
        // Provides K1 frontend APIs, scripting, analysis API
        bundledPlugin("org.jetbrains.kotlin")
        // jupyter/psi, jupyter/core
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.core)
}
