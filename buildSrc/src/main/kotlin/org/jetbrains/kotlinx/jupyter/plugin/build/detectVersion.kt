package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project

fun Any?.isTrue() = (this != null) && (this.toString() == "true")

const val DEV_CHANNEL_NAME = "Dev"
const val STABLE_CHANNEL_NAME = "Stable"

fun Project.detectVersion(): String {
    val buildNumber = rootProject.findProperty("build.number") as? String
    val isRelease = rootProject.findProperty("build.release").isTrue()
    val channel = if (isRelease) STABLE_CHANNEL_NAME else DEV_CHANNEL_NAME
    val pluginVersionStub = rootProject.findProperty("pluginVersionStub")
    return if (buildNumber != null) {
        if (hasProperty("build.number.detection")) {
            "$pluginVersionStub-$channel.$buildNumber"
        } else {
            buildNumber
        }
    }
    else {
        "$pluginVersionStub-$channel"
    }
}
