// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.executor.submitSilentTask
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterTaskPriority
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.JupyterThemeChangedEvent
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.colorThemes.ThemeChangedListener
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.util.generateColorSchemeChangeCode
import com.intellij.kotlin.jupyter.core.util.getNotebookTheme
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookSession
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import java.util.concurrent.atomic.AtomicReference

internal class KotlinNotebookEditorColorsListener : ThemeChangedListener {
    private val sessionToTheme = ConcurrentCollectionFactory.createConcurrentMap<
            JupyterNotebookSessionId, AtomicReference<ColorScheme>
            >()

    override suspend fun themeChanged(event: JupyterThemeChangedEvent) {
        val session = event.session ?: return
        val notebookFile = event.editor.notebookFileOrNull ?: return
        val editor = event.editor
        val project = editor.project ?: return
        if (project.isDisposed) return
        if (!session.isKotlinNotebookSession()) return

        val theme = getActualTheme(session) ?: return
        val changeCode = generateColorSchemeChangeCode(theme)
            .takeIf { it.isNotBlank() } ?: return

        JupyterExecutionManager.getInstance(project, notebookFile).submitSilentTask(changeCode, JupyterTaskPriority.HIGH)
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
