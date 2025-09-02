// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterFileExecutionQueue
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.scriptLoader.utils.launchBackground
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.editor.Editor
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel

/**
 * If the session is running, the user will be prompted to shut it down.
 * If they agree, [action] will be run, and then the session will be shut down.
 * If they don't, [action] won't be run.
 * If there's no session, [action] will be run immediately.
 *
 * It doesn't do anything for non-Kotlin notebooks.
 */
inline fun promptSessionShutdownIfNeeded(
    notebookFile: BackedNotebookVirtualFile,
    notebookEditor: Editor,
    crossinline action: () -> Unit
) {
    val project = notebookEditor.project
    if (!notebookFile.isKotlinNotebook || project == null) return

    if (!JupyterRuntimeService.getInstance(project).hasActiveSession(notebookFile.file)) {
        action()
        return
    }

    dialog(
        title = KotlinNotebookBundle.message("dialog.title.session.shutdown.prompt"),
        panel = panel {
            row {
                text(KotlinNotebookBundle.message("label.session.shutdown.prompt"))
            }
        },
        ok = {
            action()
            val project = notebookEditor.project ?: return@dialog null
            val notebookFile = notebookEditor.notebookFileOrNull ?: return@dialog null

            launchBackground {
                JupyterFileExecutionQueue.getInstance(project, notebookFile).killExecution()
            }
            null
        }
    ).show()
}