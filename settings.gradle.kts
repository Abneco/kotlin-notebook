@file:Suppress("UnstableApiUsage")

rootProject.name = "kotlin-jupyter-plugin-extension"

pluginManagement {
    val kotlinVersion: String by settings
    val ktlintVersion: String by settings
    val detektVersion: String by settings
    val changelogVersion: String by settings

    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()

        class TeamcitySettings(
            val url: String,
            val projectId: String
        )
        val teamcityRepos = listOf(
            TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Artifacts"),
            TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Artifacts")
        )
        for (teamcity in teamcityRepos) {
            maven("${teamcity.url}/guestAuth/app/rest/builds/buildType:(id:${teamcity.projectId}),number:$kotlinVersion,branch:default:any/artifacts/content/maven")
        }
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
            }
        }
    }

    plugins {
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
        id("io.gitlab.arturbosch.detekt") version detektVersion
        id("org.jetbrains.changelog") version changelogVersion
        kotlin("jvm") version kotlinVersion
    }
}
