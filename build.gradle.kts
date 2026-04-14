plugins {
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlin.jvm) apply false
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

// Provide Maven Central and the kotlin-jupyter Maven repo to all subprojects
allprojects {
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
        // EAP / nightly snapshots for branch 262
        maven("https://cache-redirector.jetbrains.com/intellij.jetbrains.com/intellij-repository/nightly")
        maven("https://cache-redirector.jetbrains.com/intellij.jetbrains.com/intellij-repository/snapshots")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate(providers.gradleProperty("intellijPlatformVersion")) {
            useInstaller = false
        }

        // Plugin dependencies – these become binary deps in the standalone repository
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.intellij.notebooks.core")
        bundledPlugin("com.intellij.database")
        bundledPlugin("com.intellij.debugger.collections.visualizer")
        bundledPlugin("com.intellij.performanceTesting")

        // Platform module dependency
        bundledModule("intellij.java.backend")

        pluginVerifier()
        zipSigner()
    }

    // Content modules
    pluginModule(implementation(project(":core")))
    pluginModule(implementation(project(":tables")))
    pluginModule(implementation(project(":sql")))
    pluginModule(implementation(project(":plots")))
    pluginModule(implementation(project(":export:pdf")))
    pluginModule(implementation(project(":buildSystems:gradle")))
    pluginModule(implementation(project(":liveTemplates")))
    pluginModule(implementation(project(":k1")))
    pluginModule(implementation(project(":k2")))
    pluginModule(implementation(project(":debug")))
    pluginModule(implementation(project(":debug:renders")))
    pluginModule(implementation(project(":notekit")))
    pluginModule(implementation(project(":performancePlugin")))
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
