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
        // Kotlin plugin: KotlinLanguage
        bundledPlugin("org.jetbrains.kotlin")
        // jupyter/core, jupyter/psi, dataspell/jupyter/sql/common
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        // dbe/database
        bundledPlugin("com.intellij.database")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
}
