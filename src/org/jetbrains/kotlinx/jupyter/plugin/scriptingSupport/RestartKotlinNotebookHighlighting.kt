// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction

class RestartKotlinNotebookHighlighting: DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val openFiles = FileEditorManager.getInstance(project).getOpenFiles()

        JupyterCompilerService.getInstance(project)
            .restartHighlighting(openFiles.toList())
    }
}