import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Java support
    id("java")
    // Kotlin support
    kotlin("jvm") version "1.4.20"
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "0.5.0"
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "0.6.2"
    // detekt linter - read more: https://detekt.github.io/detekt/gradle.html
    id("io.gitlab.arturbosch.detekt") version "1.14.1"
    // ktlint linter - read more: https://github.com/JLLeitschuh/ktlint-gradle
    id("org.jlleitschuh.gradle.ktlint")

    // id("org.anarres.jarjar")
}

// Import variables from gradle.properties file
val pluginGroup: String by project
// `pluginName_` variable ends with `_` because of the collision with Kotlin magic getter in the `intellij` closure.
// Read more about the issue: https://github.com/JetBrains/intellij-platform-plugin-template/issues/29
val pluginName_: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project

val platformType: String by project
val platformVersion: String by project
val platformLocalPath: String by project
// val platformPlugins: String by project
val platformDownloadSources: String by project

val notebookApiVersion: String by project
val kotlinVersion: String by project
val intellijBuildNumber: String by project

group = pluginGroup
version = pluginVersion

// Configure project's dependencies
repositories {
    mavenCentral()
    mavenLocal()
    jcenter()

    class TeamcitySettings(
        val url: String,
        val projectId: String
    )

    val apiPrefix = "guestAuth/app/rest/builds"
    val teamcityRepos = listOf(
        TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Aggregate"),
        TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Aggregate")
    )
    for (teamcity in teamcityRepos) {
        maven("${teamcity.url}/$apiPrefix/buildType:(id:${teamcity.projectId}),number:$kotlinVersion,branch:default:any/artifacts/content/maven")
    }

    val teamcityUrl = "https://buildserver.labs.intellij.net"
    val buildId = "ijplatform_IjPlatform202_Idea_Installers"
    maven("$teamcityUrl/$apiPrefix/buildType:(id:$buildId),number:$intellijBuildNumber,branch:default:any/artifacts/content/maven-artifacts")

    maven("https://dl.bintray.com/ileasile/kotlin-datascience-ileasile")
}
dependencies {
    fun ExternalModuleDependency.excludeKotlin(dependency: String) {
        exclude("org.jetbrains.kotlin", "kotlin-$dependency")
    }

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.14.1")

    implementation("org.jetbrains.kotlinx.jupyter:compiler:$notebookApiVersion") {
        excludeKotlin("stdlib")
        excludeKotlin("reflect")
        excludeKotlin("stdlib-common")
    }

    compileOnly(kotlin("scripting-jvm", kotlinVersion))
    compileOnly(kotlin("scripting-compiler", kotlinVersion))
    compileOnly(kotlin("scripting-compiler-impl", kotlinVersion))
    compileOnly(kotlin("scripting-intellij", kotlinVersion))
}

// Configure gradle-intellij-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName = pluginName_
    localPath = platformLocalPath
    // version = platformVersion
    type = platformType
    downloadSources = platformDownloadSources.toBoolean()
    updateSinceUntilBuild = true

    pluginsRepo {
        marketplace()
        val teamcityUrl = "https://buildserver.labs.intellij.net"
        val apiPrefix = "guestAuth/app/rest/builds"

        custom("$teamcityUrl/$apiPrefix/buildType:(id:Kotlin_KotlinDev_Aggregate),number:$kotlinVersion,branch:default:any/artifacts/content/updatePlugins-IJ2020.2.xml")

        val buildId = "ijplatform_IjPlatform202_PyCharm_InstallersBuild"
        val pathToPluginsRepo = "PY-plugins/plugins.xml"
        custom("$teamcityUrl/$apiPrefix/buildType:(id:$buildId),number:$intellijBuildNumber,branch:default:any/artifacts/content/$pathToPluginsRepo")
    }

    // Plugin Dependencies
    setPlugins(
        "org.jetbrains.kotlin:$kotlinVersion-IJ2020.2-1",
        "Pythonid:$intellijBuildNumber",
        "java"
    )
}

// Configure detekt plugin.
// Read more: https://detekt.github.io/detekt/kotlindsl.html
detekt {
    config = files("./detekt-config.yml")
    buildUponDefaultConfig = true

    reports {
        html.enabled = false
        xml.enabled = false
        txt.enabled = false
    }
}

tasks {
    // Set the compatibility versions to 1.8
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    listOf("compileKotlin", "compileTestKotlin").forEach {
        getByName<KotlinCompile>(it) {
            kotlinOptions.jvmTarget = "1.8"
            kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility")
        }
    }

    withType<Detekt> {
        jvmTarget = "1.8"
    }

    patchPluginXml {
        version(pluginVersion)
        sinceBuild(pluginSinceBuild)
        untilBuild(pluginUntilBuild)

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription(
            closure {
                File("./README.md").readText().lines().run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"

                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }.joinToString("\n").run { markdownToHTML(this) }
            }
        )

        // Get the latest available change notes from the changelog file
        changeNotes(
            closure {
                changelog.getLatest().toHTML()
            }
        )
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://jetbrains.org/intellij/sdk/docs/tutorials/build_system/deployment.html#specifying-a-release-channel
        channels(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
    }
}
