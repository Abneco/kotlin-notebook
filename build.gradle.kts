import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.plugin.build.BuildLocator
import org.jetbrains.kotlinx.jupyter.plugin.build.GuestAuth
import org.jetbrains.kotlinx.jupyter.plugin.build.INTERNAL_TEAMCITY
import org.jetbrains.kotlinx.jupyter.plugin.build.PUBLIC_TEAMCITY
import org.jetbrains.kotlinx.jupyter.plugin.build.detectDepVersions
import org.jetbrains.kotlinx.jupyter.plugin.build.detectVersion
import org.jetbrains.kotlinx.jupyter.plugin.build.printTcBuildNumber
import org.jetbrains.kotlinx.jupyter.plugin.build.tcMaven
import org.jetbrains.kotlinx.jupyter.plugin.build.teamcity

plugins {
    // Java support
    id("java")
    // Kotlin support
    kotlin("jvm")
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij")
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog")
    // detekt linter - read more: https://detekt.github.io/detekt/gradle.html
    id("io.gitlab.arturbosch.detekt")
    // ktlint linter - read more: https://github.com/JLLeitschuh/ktlint-gradle
    id("org.jlleitschuh.gradle.ktlint")
}

val pluginGroup: String by project

@Suppress("PropertyName")
val pluginName_: String by project

@Suppress("PropertyName")
val jvmTarget_: String by project

val runIdeMaxMemory: String by project

val platformType: String by project
val platformDownloadSources: String by project
val notebookApiVersion: String by project
val kotlinVersion: String by project
val junitVersion: String by project
val kotestVersion: String by project
val detektVersion: String by project

val pluginVersion = detectVersion()
val depVersions = detectDepVersions()

val pluginSinceBuild = depVersions.pluginSinceBuild
val pluginUntilBuild = depVersions.pluginUntilBuild
val platformVersion = depVersions.platformVersion
val platformLocalPath = depVersions.platformLocalPath
val intellijBuildNumber = depVersions.intellijBuildNumber
val kotlinPluginBuildNumber = depVersions.kotlinPluginBuildNumber

group = pluginGroup
version = pluginVersion.version

printTcBuildNumber(pluginVersion.version)

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")

    class TeamcitySettings(
        val url: String,
        val projectId: String
    )

    val teamcityRepos = listOf(
        TeamcitySettings(PUBLIC_TEAMCITY, "Kotlin_KotlinPublic_Artifacts"),
        TeamcitySettings(INTERNAL_TEAMCITY, "Kotlin_KotlinDev_Artifacts")
    )
    for (teamcity in teamcityRepos) {
        tcMaven(teamcity.url, GuestAuth, BuildLocator(teamcity.projectId, kotlinVersion), "maven")
    }

    val buildId = "ijplatform_master_Idea_Installers"
    tcMaven(INTERNAL_TEAMCITY, GuestAuth, BuildLocator(buildId, intellijBuildNumber), "maven-artifacts")
}

dependencies {
    fun ExternalModuleDependency.excludeKotlin(dependency: String) {
        exclude("org.jetbrains.kotlin", "kotlin-$dependency")
    }

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")

    implementation("org.jetbrains.kotlinx:kotlin-jupyter-shared-compiler:$notebookApiVersion") {
        excludeKotlin("stdlib")
        excludeKotlin("reflect")
        excludeKotlin("stdlib-common")
        excludeKotlin("scripting-dependencies")
    }

    implementation(kotlin("scripting-dependencies", kotlinVersion) as String) { isTransitive = false }

    testImplementation("junit:junit:$junitVersion")
    testImplementation("io.kotest:kotest-runner-junit4:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen")
        }
    }
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

        teamcity(
            "ijplatform_master_PyCharm_InstallersBuild",
            intellijBuildNumber,
            "PY-plugins/plugins.xml"
        )

        teamcity(
            "ijplatform_master_KotlinIdeArtifact",
            kotlinPluginBuildNumber,
            "plugin.xml"
        )
    }

    // Plugin Dependencies
    setPlugins(
        "org.jetbrains.kotlin:$kotlinPluginBuildNumber",
        "Pythonid:$intellijBuildNumber",
        "java"
    )
}

changelog {
    version = pluginVersion.version
    groups = listOf("Added", "Fixed")
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
        sourceCompatibility = jvmTarget_
        targetCompatibility = jvmTarget_
    }

    listOf("compileKotlin", "compileTestKotlin").forEach {
        getByName<KotlinCompile>(it) {
            kotlinOptions.jvmTarget = jvmTarget_
            kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility")
        }
    }

    withType<Test> {
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<Detekt> {
        jvmTarget = jvmTarget_
    }

    runIde {
        jvmArgs = listOf("-Xmx$runIdeMaxMemory")
    }

    patchPluginXml {
        version(pluginVersion.version)
        sinceBuild(pluginSinceBuild)
        untilBuild(pluginUntilBuild)

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription(
            closure {
                File(projectDir, "README.md").readText().lines().run {
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
        token(project.findProperty("intellij.marketplace.publish.token"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://jetbrains.org/intellij/sdk/docs/tutorials/build_system/deployment.html#specifying-a-release-channel
        channels(pluginVersion.channel)
    }
}
