// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showAbsentDependencies
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showOutdatedDependencies
import org.jetbrains.kotlinx.jupyter.plugin.stats.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.SKIP_PROJECT_BUILD_COMMENT
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.PreExecutionSourceModifier
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpec
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JupyterKotlinPreExecutionSourceModifier : PreExecutionSourceModifier, Disposable {
    private var firstRun: Boolean = true
    private val artifactsCache = mutableMapOf<String, MutableSet<String>>()
    private val artifactsCacheLock = ReentrantLock()

    init {
        Disposer.register(KotlinNotebookPluginUpdater.getInstance(), this)
        registerSessionDeleteListener()
    }

    override fun amendSource(project: Project, sessionId: String, kernelSpec: JupyterKernelSpec, source: String): String? {
        if (kernelSpec.language != "kotlin") return null

        return addProjectDependencies(project, sessionId, source)
    }

    private fun addProjectDependencies(project: Project, sessionId: String, source: String): String? {
        if (source.contains(SKIP_PROJECT_BUILD_COMMENT)) return null
        if (looksLikeReplCommand(source)) return null

        val artifactsService = JupyterKotlinProjectArtifactsService.getInstance(project)
        val (buildProjectResult, libraries) = runBlocking {
            Pair(artifactsService.buildProject(), artifactsService.getLibraries())
        }
        val allArtifacts = buildProjectResult.artifacts + libraries
        val newArtifacts = getOnlyNewArtifacts(sessionId, allArtifacts)
        if (newArtifacts.isEmpty()) return null

        when (buildProjectResult.state) {
            DependenciesState.OUTDATED -> showOutdatedDependencies(project)
            DependenciesState.ABSENT -> {
                showAbsentDependencies(project)
                if (firstRun) {
                    firstRun = false
                    throw RuntimeException(JupyterKotlinBundle.message("kotlin.jupyter.dependencies.build.error.throwable"))
                }
                return null
            }
            else -> {}
        }
        firstRun = false

        val amendedSource = buildString {
            for (artifact in newArtifacts) {
                append("@file:DependsOn(\"")
                append(StringUtil.escapeStringCharacters(artifact))
                append("\")\n")
            }
            append(source)
        }
        return amendedSource
    }

    override fun dispose() {
        artifactsCacheLock.withLock {
            artifactsCache.clear()
        }
    }

    private fun getOnlyNewArtifacts(sessionId: String, allArtifacts: Collection<String>): Collection<String> {
        return artifactsCacheLock.withLock {
            val oldArtifacts = artifactsCache.getOrPut(sessionId) { mutableSetOf() }
            val newArtifacts = allArtifacts.filter { it !in oldArtifacts }
            oldArtifacts.addAll(newArtifacts)
            newArtifacts
        }
    }

    private fun registerSessionDeleteListener() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            JupyterRuntimeService.Listener.TOPIC,
            object : JupyterRuntimeService.Listener {
                override fun sessionDeleted(session: JupyterNotebookSession) {
                    artifactsCacheLock.withLock {
                        artifactsCache.remove(session.sessionId)
                    }
                }
            }
        )
    }
}
