package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project

fun printTcBuildNumber(buildNumber: String) {
    println("##teamcity[buildNumber '$buildNumber']")
}

fun printTcParam(name: String, value: String) {
    println("##teamcity[setParameter name='$name' value='$value']")
}

fun Project.registerDetectVersionsTask() {
    tasks.register("detectVersionsForTC") {
        outputs.upToDateWhen { false }

        doLast {
            val pluginVersion = detectVersion()
            val depVersions = detectDepVersions()

            printTcBuildNumber(pluginVersion.version)
            printTcParam("gen.intellij.build.number", depVersions.intellijBuildNumber)
        }
    }
}
