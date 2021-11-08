// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import java.io.File

object KotlinJupyterResourcesUtil {
    const val KOTLIN_JUPYTER_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"

    private val LOG = logger<KotlinJupyterResourcesUtil>()

    fun pluginDescriptor() = PluginManagerCore.getPlugin(PluginId.getId(KOTLIN_JUPYTER_PLUGIN_ID))

    fun getPluginResource(path: String): File? {
        LOG.info("Getting plugin directory: $path")
        val pluginDir = pluginDescriptor()?.pluginPath ?: return null
        LOG.info("Resolved plugin path: $pluginDir")
        val pluginResourceDir = pluginDir.resolve(path).toFile().also { dir ->
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

        return pluginResourceDir.takeIf { it.isNotEmptyDirectory }
    }

    fun getKernelJarsFromResources(): File? {
        return getPluginResource("kernelJars")
    }
}
