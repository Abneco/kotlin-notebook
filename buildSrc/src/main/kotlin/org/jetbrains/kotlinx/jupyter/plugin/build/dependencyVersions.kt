package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project
import java.io.File

data class DependenciesVersions(
    val pluginSinceBuild: String,
    val pluginUntilBuild: String,
    val platformVersion: String,
    val platformLocalPath: String,
    val intellijBuildNumber: String,
    val kotlinPluginBuildNumber: String
)

private fun Project.prop(name: String, default: () -> String): String {
    return findProperty(name) as? String ?: default()
}

fun String.substitute(replaceMap: Map<String, String>): String {
    return replaceMap.asSequence().fold(this) { s, template ->
        s.replace(template.key, template.value)
    }
}

fun Project.detectLocalPath(replaceMap: Map<String, String>, platformVersion: String): String {
    return prop("intellij.platform.local.path") {
        if (findProperty("intellij.platform.local.prefer.archive").isTrue()) {
            val archivePathStub = findProperty("intellij.platform.local.archive") as String
            val archivePath = projectDir.resolve(archivePathStub.substitute(replaceMap))
            useIdeaArchive(archivePath, platformVersion)
        } else {
            val localPathStub = findProperty("intellij.platform.local.path.stub") as String
            localPathStub.substitute(replaceMap)
        }
    }
}

fun Project.detectDepVersions(): DependenciesVersions {
    val ijVer = findProperty("intellij.platform.version.main") as String
    val ijBuild = findProperty("intellij.platform.version.build") as String
    val ijKotlinBuild = findProperty("intellij.kotlin.build") as String

    val platformVersion = "$ijVer.$ijBuild"
    val localPath = detectLocalPath(mapOf(
        "{ijVer}" to ijVer,
        "{ijBuild}" to ijBuild
    ), platformVersion)

    return DependenciesVersions(
        pluginSinceBuild = prop("final.plugin.since.build") { platformVersion },
        pluginUntilBuild = prop("final.plugin.until.build") { "$ijVer.*" },
        platformVersion = prop("final.intellij.platform.version") { platformVersion },
        platformLocalPath = localPath,
        intellijBuildNumber = prop("final.intellij.build.number") { platformVersion },
        kotlinPluginBuildNumber = prop("final.intellij.kotlin.build.number") { "$ijVer-$ijKotlinBuild-IJ$ijBuild" }
    )
}
