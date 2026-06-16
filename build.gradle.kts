import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

plugins {
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "org.jetbrains.plugins.kotlin.jupyter"
version = providers.gradleProperty("pluginVersion").get()

// Plugin descriptor lives in plugin/resources to match the upstream monorepo layout
sourceSets {
    main {
        resources {
            srcDirs("plugin/resources")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

// Apply the module plugin to every subproject so they can declare intellijPlatform dependencies.
// Disable Java bytecode instrumentation globally — this is a Kotlin-only plugin; the
// java-compiler-ant-tasks are not published for nightly builds.
val intellijPlatformModulePluginId = libs.plugins.intellijPlatformModule.get().pluginId
subprojects {
    @Suppress("AvoidApplyPluginMethod")
    apply(plugin = intellijPlatformModulePluginId)
    extensions.configure<IntelliJPlatformExtension> {
        instrumentCode = false
    }
}

// Shared repository and resolution configuration for all projects
allprojects {
    val intellijPlatformVersion = providers.gradleProperty("intellijPlatformVersion")
    configurations.all {
        resolutionStrategy {
            // Nightly builds only publish 262-SNAPSHOT, not exact build numbers like 262.3925.
            // Force these to resolve from the nightly repo using the snapshot coordinates.
            force(
                "com.jetbrains.intellij.platform:test-framework:${intellijPlatformVersion.get()}",
                "com.jetbrains.intellij.platform:test-framework-junit5:${intellijPlatformVersion.get()}",
            )
        }
    }
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        intellijPlatform {
            defaultRepositories()
            // Nightly snapshots for branch 262
            nightly()
        }
    }

    // we pretend that the root project is named "intellij.kotlin.jupyter",
    // so that module XMLs of modules referenced in plugin.xml can be found in corresponding JARs.
    //
    // in other words, when runtime sees <module name="intellij.kotlin.jupyter.core"/>,
    // it expects to find intellij.kotlin.jupyter.core.jar
    tasks.composedJar {
        // logic copied from ComposedJarTask sources,
        // with the exception that rootProjectName is replaced with "intellij.kotlin.jupyter"
        archiveBaseName.convention(module.map { isIntellijModule ->
            val moduleName = project.path
                .removePrefix(":")
                .replace(':', '.')
                .ifBlank { project.name }

            when (isIntellijModule) {
                true -> "intellij.kotlin.jupyter.$moduleName"
                false -> project.name
            }
        })
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate(providers.gradleProperty("intellijPlatformVersion")) {
            useInstaller = false
        }

        // Plugin dependencies – these become binary deps in the standalone repository
        bundledPlugin("org.jetbrains.kotlin")
        plugin(libs.plugins.nonBundledIntellij.jupyter.map { it.toString() })
        plugin(libs.plugins.nonBundledIntellij.notebooksCore.map { it.toString() })
        bundledPlugin("com.intellij.database")
        bundledPlugin("com.intellij.debugger.collections.visualizer")
        bundledPlugin("com.jetbrains.performancePlugin")
        bundledPlugin("org.jetbrains.plugins.github")

        // Platform module dependency
        bundledModule("intellij.java.backend")

        pluginVerifier()
        zipSigner()

        // Content modules
        pluginModule(implementation(projects.core))
        pluginModule(implementation(projects.tables))
        pluginModule(implementation(projects.sql))
        pluginModule(implementation(projects.plots))
        pluginModule(implementation(projects.export.pdf))
        pluginModule(implementation(projects.buildSystems.gradle))
        pluginModule(implementation(projects.liveTemplates))
        pluginModule(implementation(projects.k2))
        pluginModule(implementation(projects.debug))
        pluginModule(implementation(projects.debug.renders))
        pluginModule(implementation(projects.notekit))
        pluginModule(implementation(projects.performancePlugin))
    }
}

intellijPlatform {
    instrumentCode = false

    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
