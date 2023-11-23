// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.IdeaUltimateProperties
import org.jetbrains.intellij.build.impl.BuildContextImpl

object KotlinNotebookPluginBuildTarget {
    @Suppress("RAW_RUN_BLOCKING")
    @JvmStatic
    fun main(args: Array<String>) = runBlocking(Dispatchers.Default) {
        val ultimateHome = IdeaProjectLoaderUtil.guessUltimateHome(javaClass)
        val context = BuildContextImpl.createContext(
            communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass),
            projectHome = IdeaProjectLoaderUtil.guessUltimateHome(javaClass),
            productProperties = IdeaUltimateProperties(ultimateHome),
        )

        BuildTasks.create(context).buildNonBundledPlugins(listOf(
            "intellij.kotlin.jupyter",
        ))

        context.notifyArtifactBuilt(context.paths.artifactDir)
    }
}
