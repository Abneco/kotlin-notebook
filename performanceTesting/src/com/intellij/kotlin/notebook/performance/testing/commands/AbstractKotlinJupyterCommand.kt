// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.notebook.performance.testing.commands

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.executor.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.actions.CellExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterExecutionStatus
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.notebooks.visualization.ui.ProgressStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import kotlin.time.measureTime

abstract class AbstractKotlinJupyterCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
    protected suspend fun getBackedFile(context: PlaybackContext): BackedNotebookVirtualFile? {
        val project = context.project
        val editor = withContext(Dispatchers.EDT) {
            FileEditorManager.getInstance(project).selectedEditor
        } ?: return null

        val file = editor.file ?: return null
        if (!BackedNotebookVirtualFile.isBacked(file)) return null

        return BackedNotebookVirtualFile.takeBackend(file)
    }

    protected suspend fun waitExecutionFinishes(context: PlaybackContext, timeoutMs: Long) {
        val project = context.project
        val backedFile = getBackedFile(context) ?: return
        val executionManager = JupyterExecutionManager.getInstance(project, backedFile)
        val executionState = JupyterExecutionState.getInstance(project, backedFile)

        thisLogger().info("waitExecutionFinishes started for ${backedFile.file.name}")

        fun isIdle(): Boolean {
            val isBusy = executionManager.isKernelWorking()
            val queued = executionState.getQueued()
            val lastExecutingStatus = executionState.lastExecutingStatus
            val hasActiveTasks = lastExecutingStatus == ProgressStatus.RUNNING || queued.isNotEmpty()
            thisLogger().info("waitExecutionFinishes: isBusy=$isBusy, lastExecutingStatus=$lastExecutingStatus, queuedCount=${queued.size}")
            return !isBusy && !hasActiveTasks
        }

        val duration = measureTime {
            if (isIdle()) {
                thisLogger().info("waitExecutionFinishes: already idle for ${backedFile.file.name}")
            } else {
                val deferred = CompletableDeferred<Unit>()
                val disposable: Disposable = Disposer.newDisposable("waitExecutionFinishesListener")

                val listener = object : CellExecutionListener {
                    override fun executionStopped(
                        cellPointer: NotebookIntervalPointer,
                        jupyterStatus: JupyterExecutionStatus,
                        endTime: ZonedDateTime,
                    ) {
                        if (isIdle() && !deferred.isCompleted) deferred.complete(Unit)
                    }
                }

                try {
                    backedFile.notebook.listeners.cellExecutionListeners.addListener(listener, disposable)
                    withTimeoutOrNull(timeoutMs) {
                        deferred.await()
                    } ?: thisLogger().warn("waitExecutionFinishes timed out for ${backedFile.file.name}")
                } finally {
                    Disposer.dispose(disposable)
                }
            }
        }
        thisLogger().info("waitExecutionFinishes finished for ${backedFile.file.name} in ${duration.inWholeMilliseconds}ms")
    }
}
