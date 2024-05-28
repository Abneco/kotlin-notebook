// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookCodegen
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.JupyterThemeChangedEvent
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.ThemeChangedListener

private class KotlinNotebookEditorColorsListener : ThemeChangedListener {
    override fun themeChanged(event: JupyterThemeChangedEvent) {
        val session = event.session ?: return
        val editor = event.editor
        val project = editor.project ?: return
        if (project.isDisposed) return
        if (!session.isKotlinNotebookSession()) return

        val scheme = editor.colorsScheme
        val changeCode = KotlinNotebookCodegen.generateColorSchemeChangeCode().takeIf { it.isNotBlank() } ?: return

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

    companion object {
        val LOG = logger<KotlinNotebookEditorColorsListener>()
    }
}
