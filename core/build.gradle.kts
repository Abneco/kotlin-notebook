plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellijPlatformModule)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaIdeApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaNonPublicApi",
            "-opt-in=org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi",
            "-Xcontext-parameters",
        )
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Kotlin plugin: scripting, analysis API, kotlin base APIs
        bundledPlugin("org.jetbrains.kotlin")
        // intellij.jupyter plugin: jupyter/core, jupyter/psi, jupyter/execution
        bundledPlugin("intellij.jupyter")
        // Java APIs
        bundledModule("intellij.java.backend")
    }

    // Bundled third-party libs (shipped with the plugin)
    api(libs.kotlin.jupyter.ws.server) {
        // Exclude kotlin-jupyter protocol artifacts – already provided by the jupyter plugin
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-protocol-api")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-protocol")
    }
    api(libs.kotlin.jupyter.shared.compiler)
    api(libs.kotlin.jupyter.api)
    api(libs.kotlin.jupyter.lib)
    api(libs.kotlin.jupyter.common.dependencies)
    implementation(libs.clikt)

    // Provided by the IntelliJ platform / kotlin plugin at runtime
    compileOnly(libs.kotlinx.serialization.core)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.slf4j.api)
}
