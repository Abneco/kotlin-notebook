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
        bundledPlugin("org.jetbrains.kotlin")
        // Provides debugger-collections-visualizer modules
        bundledPlugin("com.intellij.debugger.collections.visualizer")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.debug)

    compileOnly(libs.kotlinx.coroutines.core)
}
