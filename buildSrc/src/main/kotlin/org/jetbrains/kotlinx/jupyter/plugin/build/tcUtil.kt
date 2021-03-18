package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project

fun printTcParam(name: String, value: String) {
    println("##teamcity[setParameter name='$name' value='$value']")
}

fun Project.registerDetectVersionsTask() {
    tasks.register("detectVersionsForTC") {
        it.outputs.upToDateWhen { false }

        it.doLast {
            val pluginVersion = detectVersion()
            val depVersions = detectDepVersions()

            println("##teamcity[buildNumber '$pluginVersion']")
            printTcParam("gen.intellij.build.number", depVersions.intellijBuildNumber)
        }
    }
}
