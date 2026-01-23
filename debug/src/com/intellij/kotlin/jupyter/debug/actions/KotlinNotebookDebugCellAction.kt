// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.actions

import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.scriptLoader.utils.launchBackground
import com.intellij.jupyter.core.jupyter.helper.JupyterHelper
import com.intellij.jupyter.core.jupyter.helper.jupyterEditor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.settings.actions.KotlinNotebookEditorActionBase
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesSupported
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.awaitAll

/**
 * Action to run the current cell with debugging enabled.
 * Unlike normal Run Cell, this creates a non-silent debug session
 * that shows the Debug tool window and respects project breakpoints.
 */
class KotlinNotebookDebugCellAction : KotlinNotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val dataContext = event.dataContext
        val editor = dataContext.jupyterEditor ?: return

        val project = editor.project ?: return
        val notebookVirtualFile = dataContext.notebookFile ?: return
        if (!notebookVirtualFile.file.isKotlinNotebook) {
            return
        }

        val intervalPointers = JupyterHelper.getSelectedIntervalPointers(editor)
        if (intervalPointers.isEmpty()) return

        val debugSession = KotlinNotebookDebugSessionManager.getForFile(project, notebookVirtualFile)
        val fileName = notebookVirtualFile.file.name

        launchBackground {
            val port = debugSession.provideFreshDebugPort()
            if (port == null) {
                LOG.warn("Could not get debug port for notebook ${fileName}")
                return@launchBackground
            }

            val config = DebugSessionConfig(
                port = port,
                silent = false
            )

            LOG.info("Starting debug session with non-silent mode for notebook ${fileName}")
            debugSession.getOrCreateVmDebuggerSession(config, forceRestart = true)

            // Execute cells with non-suspending breakpoint to allow project breakpoints to work
            debugSession.withNonSuspendingBreakpoint {
                val results = JupyterExecutionManager.getInstance(project, notebookVirtualFile)
                    .runCells(intervalPointers)
                results.awaitAll()
            }
        }
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { event ->
            val presentation = event.presentation
            val project = event.project
            val notebook = event.getKotlinNotebook()
            if (project == null || notebook == null) {
                presentation.isEnabledAndVisible = false
                return@update
            }

            val canDebugNow = event.notebookFile?.debugFeaturesSupported(project) == true
            presentation.isEnabled = canDebugNow
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}
