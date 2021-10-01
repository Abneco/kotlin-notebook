// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.File

const val KOTLIN_JUPYTER_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"

fun pluginDescriptor() = PluginManagerCore.getPlugin(PluginId.getId(KOTLIN_JUPYTER_PLUGIN_ID))

fun getPluginResource(path: String): File? {
    return pluginDescriptor()?.pluginPath?.resolve(path)?.toFile()?.takeIf { it.isNotEmptyDirectory }
}

fun getKernelJarsFromResources(): File? {
    return getPluginResource("kernelJars")
}
