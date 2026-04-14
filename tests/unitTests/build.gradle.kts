import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=com.intellij.openapi.util.IntellijInternalApi",
            "-opt-in=org.jetbrains.kotlin.K1Deprecation",
        )
    }
}

sourceSets {
    test {
        kotlin.srcDirs("testSrc")
        resources.srcDirs("testData")
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.intellij.notebooks.core")
        bundledPlugin("com.intellij.database")
        bundledModule("intellij.java.backend")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }

    testImplementation(projects.core)
    testImplementation(projects.tables)
    testImplementation(projects.export.pdf)
    testImplementation(projects.k1)
    testImplementation(projects.k2)
    testImplementation(projects.debug)
    testImplementation(projects.plots)
    testImplementation(projects.sql)

    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.jackson.core)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.junit4)
}
