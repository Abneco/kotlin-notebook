rootProject.name = "kotlin-jupyter-plugin-extension"

pluginManagement {
    val kotlinVersion: String by settings
    val ktlintVersion: String by settings
    val jarjarVersion: String by settings

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
            TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Aggregate"),
            TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Aggregate")
        )
        for (teamcity in teamcityRepos) {
            maven("${teamcity.url}/guestAuth/app/rest/builds/buildType:(id:${teamcity.projectId}),number:$kotlinVersion,branch:default:any/artifacts/content/maven")
        }
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.anarres.jarjar" -> useModule("org.anarres.jarjar:jarjar-gradle:$jarjarVersion")
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
            }
        }
    }

    plugins {
        // kotlin("jvm") version kotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
        id("org.anarres.jarjar") version jarjarVersion
    }
}
