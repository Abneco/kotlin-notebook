// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.JupyterThemeChangedEvent
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.ThemeChangedListener
import com.intellij.kotlin.jupyter.core.util.generateColorSchemeChangeCode
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookSession
import com.intellij.kotlin.jupyter.core.util.getNotebookTheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import java.util.concurrent.atomic.AtomicReference

private class KotlinNotebookEditorColorsListener : ThemeChangedListener {
    private val sessionToTheme = ConcurrentCollectionFactory.createConcurrentMap<
            JupyterNotebookSessionId, AtomicReference<ColorScheme>
            >()

    override fun themeChanged(event: JupyterThemeChangedEvent) {
        val session = event.session ?: return
        val editor = event.editor
        val project = editor.project ?: return
        if (project.isDisposed) return
        if (!session.isKotlinNotebookSession()) return

        val theme = getActualTheme(session) ?: return
        val changeCode = generateColorSchemeChangeCode(theme)
            .takeIf { it.isNotBlank() } ?: return

        session.execute(
            changeCode,
            onMessageCreated = {},
            callbacks = listOf(
                object : JupyterExecutionCallbackAdapter() {
                    override fun onExecuteReply(message: JupyterMessage) {
                        LOG.debug(
                            "Kotlin Notebook session was updated with new color scheme $theme: ${message.json}"
                        )
                    }
                }
            ),
            silent = true,
        )
    }

    /**
     * Returns null if the theme is not changed.
     */
    private fun getActualTheme(
        session: JupyterNotebookSession,
    ): ColorScheme? {
        val id = session.sessionId
        val theme = sessionToTheme.getOrPut(id) {
            Disposer.register(session) {
                sessionToTheme.remove(id)
            }
            AtomicReference(null)
        }

        while (true) {
            val previous = theme.get()
            val current = getNotebookTheme()
            if (theme.compareAndSet(previous, current)) {
                return current.takeIf { it != previous }
            }
        }
    }

    companion object {
        val LOG = logger<KotlinNotebookEditorColorsListener>()
    }
}
