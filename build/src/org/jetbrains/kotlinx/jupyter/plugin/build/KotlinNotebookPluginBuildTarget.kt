// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.IdeaUltimateProperties
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.impl.BuildContextImpl

object KotlinNotebookPluginBuildTarget {
    @Suppress("RAW_RUN_BLOCKING")
    @JvmStatic
    fun main(args: Array<String>) = runBlocking(Dispatchers.Default) {
        val context = BuildContextImpl.createContext(
            projectHome = ULTIMATE_HOME,
            productProperties = IdeaUltimateProperties(ULTIMATE_HOME),
        )
        context.options.enableEmbeddedJetBrainsClient = false

        createBuildTasks(context).buildNonBundledPlugins(listOf(
            "intellij.kotlin.jupyter",
            "intellij.jupyter.plugin",
            "intellij.notebooks.core",
        ))

        context.notifyArtifactBuilt(context.paths.artifactDir)
    }
}
