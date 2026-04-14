plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellijPlatformModule)
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

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("intellij.jupyter")
        bundledPlugin("com.intellij.performanceTesting")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }

    testImplementation(project(":core"))
    testImplementation(project(":performancePlugin"))
}
