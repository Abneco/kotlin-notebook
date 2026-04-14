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
        bundledPlugin("intellij.jupyter")
        bundledModule("intellij.java.backend")
    }

    implementation(projects.core)

    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.kotlinx.coroutines.core)
}
