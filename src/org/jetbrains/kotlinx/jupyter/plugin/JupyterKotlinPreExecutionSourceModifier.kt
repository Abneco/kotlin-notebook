// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.kotlinx.jupyter.plugin.actions.KotlinJupyterDataKeys
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.PreExecutionSourceModifier

class JupyterKotlinPreExecutionSourceModifier : PreExecutionSourceModifier {
    override fun amendSource(source: String, dataContext: DataContext): String {
        val artifacts = dataContext.getData(KotlinJupyterDataKeys.PROJECT_ARTIFACTS) ?: return source
        val amendedSource = buildString {
            for (artifact in artifacts) {
                append("@file:DependsOn(\"")
                append(artifact.replace("\\", "\\\\"))
                append("\")\n")
            }
            append(source)
        }
        return amendedSource
    }
}
