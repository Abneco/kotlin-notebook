// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.IdeaUltimateProperties
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.impl.BuildContextImpl

object KotlinNotebookPluginBuildTarget {
    @Suppress("RAW_RUN_BLOCKING")
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        val options = BuildOptions().apply {
            enableEmbeddedFrontend = false
        }
        val context = BuildContextImpl.createContext(ULTIMATE_HOME, IdeaUltimateProperties(ULTIMATE_HOME), options)

        createBuildTasks(context).buildNonBundledPlugins(listOf(
            "intellij.notebooks.plugin",
            "intellij.jupyter.plugin",
            "intellij.kotlin.jupyter.plugin",
        ))

        context.notifyArtifactBuilt(context.paths.artifactDir)
    }
}
