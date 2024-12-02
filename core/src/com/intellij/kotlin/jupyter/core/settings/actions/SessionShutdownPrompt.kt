// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.jupyter.core.jupyter.connections.action.shutdownNotebook
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.editor.Editor
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import kotlin.reflect.KClass

/**
 * If the session is running, the user will be prompted to shut it down.
 * If they agree, [action] will be run, and then the session will be shut down.
 * If they don't, [action] won't be run.
 * If there's no session, [action] will be run immediately.
 *
 * Doesn't do anything for non-Kotlin notebooks.
 */
internal inline fun promptSessionShutdownIfNeeded(
    classForLogging: KClass<*>,
    notebookEditor: Editor,
    crossinline action: () -> Unit
) {
    val notebookFile = notebookEditor.notebookFile
    if (!notebookFile.isKotlinNotebook) return

    if (JupyterRuntimeService.Companion.getInstance(notebookEditor.project ?: return).getSession(notebookFile.file) == null) {
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
        shutdownNotebook(
          classForLogging = classForLogging,
          project = notebookEditor.project,
          editors = listOf(notebookEditor),
          virtualFiles = emptyList(),
        )
        null
      }
    ).show()
}