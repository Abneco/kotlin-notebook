package org.jetbrains.kotlinx.jupyter.plugin.build

import org.gradle.api.Project

fun Any?.isTrue() = (this != null) && (this.toString() == "true")

const val DEV_CHANNEL_NAME = "Dev"
const val STABLE_CHANNEL_NAME = "Stable"
const val DEFAULT_CHANNEL_NAME = "default"

data class PluginVersion(
    val version: String,
    val versionStub: String,
    val channel: String,
    val counter: String
) {
    companion object {
        private val versionRegex = Regex("""^(?<stub>\d+(\.\d+)*)(-(?<channel>\w+))?(\.(?<counter>\d+))$""")

        fun fromStub(versionStub: String, counter: String? = null, channel: String? = null): PluginVersion {
            val channelPart = channel?.let { "-$it" }.orEmpty()
            val counterPart = counter?.let { ".$it" }.orEmpty()
            return PluginVersion(
                "$versionStub$channelPart$counterPart",
                versionStub,
                channel ?: DEFAULT_CHANNEL_NAME,
                counter.orEmpty()
            )
        }

        fun fromVersion(version: String, channel: String? = null): PluginVersion {
            val matchResult = versionRegex.find(version)!!
            fun group(name: String) = matchResult.groups[name]?.value

            return fromStub(
                group("stub")!!,
                group("counter"),
                group("channel")
            )
        }
    }
}

fun Project.detectVersion(): PluginVersion {
    val buildNumber = rootProject.findProperty("build.number") as? String
    val isRelease = rootProject.findProperty("build.release").isTrue()
    val isPublish = rootProject.findProperty("build.publish").isTrue()
    val channel = if (isRelease) STABLE_CHANNEL_NAME else DEV_CHANNEL_NAME
    val pluginVersionStub = rootProject.findProperty("pluginVersionStub") as String
    return if (buildNumber != null) {
        if (hasProperty("build.number.detection")) {
            PluginVersion.fromStub(pluginVersionStub, buildNumber)
        } else {
            PluginVersion.fromVersion(buildNumber, if (isPublish) channel else null)
        }
    }
    else {
        PluginVersion.fromStub(pluginVersionStub, channel = channel)
    }
}
