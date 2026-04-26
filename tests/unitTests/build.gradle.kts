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
        bundledPlugin("com.intellij.java")
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.intellij.notebooks.core")
        bundledPlugin("com.intellij.database")
        bundledModule("intellij.java.backend")
        bundledPlugin("org.jetbrains.plugins.github")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java, version = "LATEST-TRUNK-SNAPSHOT")
        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.Plugin.Notebooks, version = "LATEST-TRUNK-SNAPSHOT")
        testFramework(TestFrameworkType.Plugin.Jupyter, version = "LATEST-TRUNK-SNAPSHOT")
        testFramework(TestFrameworkType.JUnit5)
    }

    testImplementation(projects.core)
    testImplementation(projects.tables)
    testImplementation(projects.export.pdf)
    //testImplementation(projects.k1)
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
    testImplementation(libs.jupyter.notebook.parser)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-tests-for-ide:2.4.0-dev-9153") {
        isTransitive = false
    //        exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
//        exclude(group = "org.jetbrains.kotlin", module = "tests-compiler-utils")
//        exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
//        exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
//        exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
//        exclude(group = "org.jetbrains.kotlin", module = "tests-spec")
    }
}
