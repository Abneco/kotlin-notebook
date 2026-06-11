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
        // jupyter/core, jupyter/psi
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
    }

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.kotlinx.coroutines.core)
}
