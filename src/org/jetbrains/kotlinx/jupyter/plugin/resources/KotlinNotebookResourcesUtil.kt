// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.extensions.PluginId

object KotlinNotebookResourcesUtil {
    private val KOTLIN_JUPYTER_PLUGIN_ID = PluginId.getId("org.jetbrains.plugins.kotlin.jupyter")

    private fun pluginDescriptor(): IdeaPluginDescriptor {
        return PluginManagerCore.getPlugin(KOTLIN_JUPYTER_PLUGIN_ID)
            ?: error("Kotlin Notebook plugin not found: " + PluginManagerCore.plugins.contentToString())
    }

    private class PluginInfo(
        val version: String,
    )

    private val pluginInfo = run {
        val descriptor = pluginDescriptor()

        PluginInfo(
            descriptor.version,
        )
    }

    internal val INTELLIJ_DEPS_REPO = RemoteRepositoryDescription(
        "intellij-dependencies",
        "Intellij Dependencies",
        "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
    )

    val pluginId: PluginId get() = KOTLIN_JUPYTER_PLUGIN_ID
    val pluginVersion: String get() = pluginInfo.version

    val isDevVersion: Boolean get() = pluginVersion.endsWith("-SNAPSHOT")
}
