// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.jetbrains.kotlinx.jupyter.plugin.actions.KotlinJupyterDataKeys
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterEditorBeforeRunActionsHandler

class JupyterKotlinBeforeRunActionsHandler: JupyterEditorBeforeRunActionsHandler {
    override suspend fun beforeRunCell(event: AnActionEvent): DataContext? {
        val project = event.project ?: return null
        val compilerService = JupyterKotlinProjectArtifactsService.getInstance(project)
        val buildResult = compilerService.buildProject()
        if (buildResult.isEmpty()) return null
        return SimpleDataContext.builder()
            .add(KotlinJupyterDataKeys.PROJECT_ARTIFACTS, buildResult)
            .build()
    }
}
