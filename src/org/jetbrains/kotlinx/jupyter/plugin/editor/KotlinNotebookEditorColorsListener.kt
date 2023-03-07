// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlinx.jupyter.plugin.session.isKotlinNotebookSession
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookCodegen
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage

private class KotlinNotebookEditorColorsListener : EditorColorsListener {
    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        if (scheme == null) return
        val changeCode = KotlinNotebookCodegen.generateColorSchemeChangeCode().takeIf { it.isNotBlank() } ?: return

        val allProjects = ProjectManager.getInstance().openProjects
        for (project in allProjects) {
            if (project.isDisposed) continue
            val runtimeService = JupyterRuntimeService.getInstance(project)
            val sessions = runtimeService.getAllSessions()
            for (session in sessions) {
                if (session.isKotlinNotebookSession()) {
                    session.execute(
                        changeCode,
                        onMessageCreated = {},
                        callbacks = listOf(
                            object : JupyterExecutionCallbackAdapter() {
                                override fun onExecuteReply(message: JupyterMessage) {
                                    LOG.debug(
                                        "Kotlin Notebook session was updated with new color scheme $scheme: ${message.json}"
                                    )
                                }
                            }
                        ),
                        silent = true,
                    )
                }
            }
        }
    }

    companion object {
        val LOG = logger<KotlinNotebookEditorColorsListener>()
    }
}