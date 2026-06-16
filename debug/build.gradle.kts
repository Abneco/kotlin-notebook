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
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        bundledModule("intellij.java.backend")
        bundledModule("intellij.java.psi")
        bundledModule("intellij.java.debugger")
        bundledModule("intellij.java.debugger.impl")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.kotlinx.coroutines.core)
}
