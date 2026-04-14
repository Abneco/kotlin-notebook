plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellijPlatformModule)
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

repositories {
    intellijPlatform {
        defaultRepositories()
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

    testImplementation(project(":core"))
    testImplementation(project(":tables"))
    testImplementation(project(":export:pdf"))
    testImplementation(project(":k1"))
    testImplementation(project(":k2"))
    testImplementation(project(":debug"))
    testImplementation(project(":plots"))
    testImplementation(project(":sql"))

    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.jackson.core)
    testImplementation(libs.jackson.databind)
}
