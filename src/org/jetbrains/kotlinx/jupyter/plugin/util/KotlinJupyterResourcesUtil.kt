// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.nio.file.Path

object KotlinJupyterResourcesUtil {
    private val KOTLIN_JUPYTER_PLUGIN_ID = PluginId.getId("org.jetbrains.plugins.kotlin.jupyter")

    private val LOG = logger<KotlinJupyterResourcesUtil>()

    private fun pluginDescriptor(): IdeaPluginDescriptor {
        return PluginManagerCore.getPlugin(KOTLIN_JUPYTER_PLUGIN_ID)
            ?: error("Kotlin Notebook plugin not found: " + PluginManagerCore.plugins.contentToString())
    }

    private class PluginInfo(
        val version: String,
        val pluginPath: Path?,
    )

    private val pluginInfo = run {
        val descriptor = pluginDescriptor()

        PluginInfo(
            descriptor.version,
            descriptor.pluginPath,
        )
    }

    // Returns file located in plugin resources at the given path, null if the resource cannot be found
    fun getPluginResource(path: String): File? {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            getPluginResourceTest(path)?.let { return it }
        }
        return getPluginResourceProd(path)
    }

    val pluginId: PluginId get() = KOTLIN_JUPYTER_PLUGIN_ID
    val pluginVersion: String get() = pluginInfo.version

    val isDevVersion: Boolean get() = pluginVersion.endsWith("-SNAPSHOT")

    private fun getPluginResourceProd(path: String): File? {
        LOG.info("Getting plugin directory: $path")
        val pluginDir = pluginInfo.pluginPath ?: return null
        LOG.info("Resolved plugin path: $pluginDir")
        return getResource(pluginDir.toFile(), path)
    }

    private fun getPluginResourceTest(path: String): File? {
        LOG.info("Getting plugin directory for test: $path")
        val resourceDir = File(".").absoluteFile.parentFile.resolve("plugins/kotlin/jupyter/resources")
        LOG.info("Resolved resource path: ${resourceDir.absolutePath}")
        return getResource(resourceDir, path)
    }

    private fun getResource(resourceDir: File, resourcePath: String): File? {
        val pluginResource = resourceDir.resolve(resourcePath).also { dir ->
            val dirType = when {
                dir.isDirectory -> "directory"
                dir.isFile -> "file"
                else -> "unknown type"
            }
            val filesList = if (dir.isNotEmptyDirectory) {
                dir.list().orEmpty().joinToString("\n", ":\n")
            } else ""

            LOG.info("Resolved resource path: $dir ($dirType)$filesList")
        }

        return pluginResource.takeIf { it.exists() }
    }
}
