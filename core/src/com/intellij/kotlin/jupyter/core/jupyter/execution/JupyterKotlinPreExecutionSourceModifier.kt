// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.jupyter.connections.execution.PreExecutionSourceModifier
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand

class JupyterKotlinPreExecutionSourceModifier : PreExecutionSourceModifier {
    override fun amendSource(
        project: Project,
        sessionId: JupyterNotebookSessionId,
        kernelSpec: JupyterKernelSpec,
        source: String
    ): String? {
        if (kernelSpec.language != "kotlin") return null

        if (source.contains(SKIP_PROJECT_BUILD_COMMENT)) return null
        if (looksLikeReplCommand(source)) return null

        val newArtifacts = JupyterKotlinProjectArtifactsService.getInstance(project).getNewArtifactsForSession(sessionId)
        if (newArtifacts.isEmpty()) return null

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
}
