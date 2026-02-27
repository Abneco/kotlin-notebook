// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionManager
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.openNotebookEditor
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.settings.KotlinNotebookDebugProjectOptionsProvider
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll

/**
 * Per-file service that handles debug-aware cell execution.
 * Decides whether to execute cells with or without debug support
 * based on breakpoint presence in the dependent module.
 */
internal class NotebookDebugCellExecutorPerFileService(
    private val project: Project,
    override val virtualFile: BackedNotebookVirtualFile,
    scope: CoroutineScope
) : NotebookPerFileChildService(virtualFile, scope) {
    /**
     * Executes cells under a debugger session or as a regular execution
     * if no breakpoints are present in the dependent module.
     */
    suspend fun executeCellsUnderDebugSession(intervalPointers: List<NotebookIntervalPointer>) {
        val debugSession = KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)
        val jupyterExecutionManager = JupyterExecutionManager.getInstanceOrCreate(project, virtualFile)
        val jupyterDebugSessionManager = JupyterDebugSessionManager.getInstance(project)
        val options = KotlinNotebookDebugProjectOptionsProvider.getInstance(project)
        val fileName = virtualFile.file.name

        val jupyterSession = jupyterExecutionManager.getOrCreateSession()

        val port = debugSession.targetDebugPort
        if (port == null) {
            LOG.warn("Debug port not available for notebook $fileName - kernel may not support debugging")
            return
        }

        val config = DebugSessionConfig(port, silent = false)
        LOG.info("Starting debug session with non-silent mode for notebook $fileName")

        val session = debugSession.getOrCreateVmDebuggerSession(config, forceRestart = false)
        if (session == null) {
            LOG.warn("Failed to create debug session for notebook $fileName")
            return
        }

        // Suppress notebook variables toolwindow to avoid race with debug window
        val previousShowVariables = options.shouldShowNotebookVariables
        options.shouldShowNotebookVariables = false

        debugSession.awaitInitialized()
        debugSession.showSessionTab()

        try {
            debugSession.withNonSuspendingBreakpoint {
                jupyterDebugSessionManager.debugInSessionStarted(virtualFile)
                jupyterExecutionManager.runCells(intervalPointers).awaitAll()
            }
        } finally {
            jupyterDebugSessionManager.debugInSessionFinished(virtualFile)
            // auto recreation if a kernel is restarted
            jupyterSession.disposingDeferred?.await()
            if (!jupyterSession.isFullyDisposed) {
                debugSession.recreateSilentSession()
            }
            project.navigateToEditorIfNeeded(virtualFile)
            options.shouldShowNotebookVariables = previousShowVariables
        }
    }

    private fun Project.navigateToEditorIfNeeded(notebookFile: BackedNotebookVirtualFile) {
        val options = KotlinNotebookDebugProjectOptionsProvider.getInstance(this)
        if (!options.shouldNavigateToEditorOnSessionStop) return

        KotlinNotebookPluginScope.invokeOnEDT {
            val editor = openNotebookEditor(notebookFile)?.editor
            editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}
