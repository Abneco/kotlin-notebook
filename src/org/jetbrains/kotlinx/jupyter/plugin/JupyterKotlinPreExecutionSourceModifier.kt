// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showAbsentDependencies
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showOutdatedDependencies
import org.jetbrains.kotlinx.jupyter.plugin.util.SKIP_PROJECT_BUILD_COMMENT
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.PreExecutionSourceModifier
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpec

class JupyterKotlinPreExecutionSourceModifier : PreExecutionSourceModifier {
    private var firstRun: Boolean = true

    override fun amendSource(project: Project, kernelSpec: JupyterKernelSpec, source: String): String? {
        if (kernelSpec.language != "kotlin") return null

        return addProjectDependencies(project, source)
    }

    private fun addProjectDependencies(project: Project, source: String): String? {
        if (source.contains(SKIP_PROJECT_BUILD_COMMENT)) return null

        val artifactsService = JupyterKotlinProjectArtifactsService.getInstance(project)
        val artifacts = runBlocking { artifactsService.buildProject() }
        if (artifacts.isEmpty()) return null

        when (artifactsService.checkProjectDependenciesStatus()) {
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
            for (artifact in artifacts) {
                append("@file:DependsOn(\"")
                append(StringUtil.escapeStringCharacters(artifact))
                append("\")\n")
            }
            append(source)
        }
        return amendedSource
    }
}
