// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import java.io.File

object KotlinJupyterResourcesUtil {
    private const val KOTLIN_JUPYTER_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"

    private val LOG = logger<KotlinJupyterResourcesUtil>()

    private fun pluginDescriptor() = PluginManagerCore.getPlugin(PluginId.getId(KOTLIN_JUPYTER_PLUGIN_ID))

    // Returns file located in plugin resources at the given path, null if the resource cannot be found
    fun getPluginResource(path: String): File? {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            getPluginResourceTest(path)?.let { return it }
        }
        return getPluginResourceProd(path)
    }

    private fun getPluginResourceProd(path: String): File? {
        LOG.info("Getting plugin directory: $path")
        val pluginDir = pluginDescriptor()?.pluginPath ?: return null
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
