import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

sourceSets {
    test {
        kotlin.srcDirs("testSrc")
        resources.srcDirs("testResources")
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.jetbrains.performancePlugin")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
        testFramework(TestFrameworkType.Starter, version = "261.22158.277")
    }

    testImplementation(projects.core)
    testImplementation(projects.performancePlugin)
}
