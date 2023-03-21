// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.plugin.util.SKIP_PROJECT_BUILD_COMMENT
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.PreExecutionSourceModifier
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpec

class JupyterKotlinPreExecutionSourceModifier : PreExecutionSourceModifier {
    override fun amendSource(project: Project, sessionId: String, kernelSpec: JupyterKernelSpec, source: String): String? {
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
