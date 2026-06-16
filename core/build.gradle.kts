plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    main {
        kotlin.srcDirs("src", "generated")
        java.srcDirs("generated")
        resources.srcDirs("resources", "resources-en")
    }
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

dependencies {
    intellijPlatform {
        // Kotlin plugin: scripting, analysis API, kotlin base APIs
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("intellij.java.aetherDependencyResolver.plugin")
        // intellij.jupyter plugin: jupyter/core, jupyter/psi, jupyter/execution
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
        // Java APIs
        bundledModule("com.intellij.java")
    }

    // Bundled third-party libs (shipped with the plugin)
    api(libs.kotlin.jupyter.ws.server) {
        // Exclude kotlin-jupyter protocol artifacts – already provided by the jupyter plugin
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-protocol-api")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-protocol")
    }

    // Provided by the IntelliJ platform / kotlin plugin at runtime
    // Provided transitively via kotlin-jupyter-ws-server at runtime; needed at compile time
    compileOnly(libs.clikt)

    compileOnly(libs.kotlinx.serialization.core)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
}
