// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.scriptLoader.utils.launchBackground
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel

/**
 * If the session is running, the user will be prompted to shut it down.
 * If they agree, [action] will be run, and then the session will be shut down.
 * If they don't, [action] won't be run.
 * If there's no session, [action] will be run immediately.
 * [action] contains [Boolean] argument to indicate whenever we are going to restart the session or not.
 *
 * It doesn't do anything for non-Kotlin notebooks.
 */
inline fun promptSessionShutdownIfNeeded(
    project: Project,
    notebookFile: BackedNotebookVirtualFile,
    crossinline action: (hasActiveJupyterSession: Boolean) -> Unit
) {
    if (!notebookFile.isKotlinNotebook) return

    if (!JupyterExecutionManager.getInstance(project, notebookFile).isKernelRunning()) {
        action(false)
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
            action(true)

            launchBackground {
                JupyterExecutionManager.getInstance(project, notebookFile).killExecution()
            }
            null
        }
    ).show()
}