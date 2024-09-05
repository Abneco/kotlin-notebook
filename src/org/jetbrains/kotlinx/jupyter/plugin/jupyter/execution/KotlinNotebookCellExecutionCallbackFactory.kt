// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackRegistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackUnregistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.editor.getCells
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class KotlinNotebookCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {
    init {
      ApplicationManager.getApplication().messageBus.connect()
          .subscribe(NotebookSessionEventListener.TOPIC, object : NotebookSessionEventListener {
              override fun sessionStarted(virtualFile: BackedNotebookVirtualFile, isAfterRestart: Boolean) {
                  executionDataLock.withWriteLock {
                      callbacksCounters.remove(virtualFile)
                  }
              }
          })
    }

    private val callbacksCounters = mutableMapOf<BackedNotebookVirtualFile, Pair<Int, PriorityQueue<Int>>>()
    private val executionDataLock = ReentrantReadWriteLock()

    private fun registerNextIndexForCallback(project: Project, file: BackedNotebookVirtualFile, cellOrd: Int?): Int {
        return executionDataLock.write {
            val (cnt, pq) = callbacksCounters[file] ?: (0 to PriorityQueue<Int>())
            if (pq.size > 1 && !pq.contains(-1)) {
                pq.add(-1)
            }
            pq.add(cnt)

            with(NotebookHighlightingService.getForFile(project, file).dataController.executionHighlightingHelper) {
                val eventData = ExecutionCallbackRegistered(cellOrd)
                onEventHappened(eventData)
            }

            callbacksCounters[file] = (cnt + 1) to pq
            cnt
        }
    }

    // returns true if it was the last registered callback and was not after single run with error
    fun unregisterCallback(project: Project, file: BackedNotebookVirtualFile, index: Int, onError: Boolean = false): Boolean {
        return executionDataLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write false
            pq.remove(index)
            val isAfterSeriesRuns = pq.size == 1 && pq.contains(-1)
            if (isAfterSeriesRuns) pq.remove(-1)
            val singleErrorRun = onError && !isAfterSeriesRuns

            with(NotebookHighlightingService.getForFile(project, file).dataController.executionHighlightingHelper) {
                val eventData =  ExecutionCallbackUnregistered(index, isAfterSeriesRuns, singleErrorRun, pq)
                onEventHappened(eventData)
            }

            pq.isEmpty() && !singleErrorRun
        }
    }

    override fun create(task: JupyterExecutionTask): JupyterExecutionCallback? {
        val cellProject = task.project ?: return null
        val file = task.notebookVirtualFile
        if (!file.file.isKotlinNotebook) return null

        val jupyterPsiCellData = runReadAction {
            val cellIndex = task.options.cellPointer?.get()?.ordinal ?: return@runReadAction null
            getCells(cellProject, task.notebookVirtualFile)?.getOrNull(cellIndex) to cellIndex
        }
        val cell = jupyterPsiCellData?.first ?: return null

        val index = registerNextIndexForCallback(cellProject, file, jupyterPsiCellData.second)

        return KotlinNotebookCellExecutionCallback(
            cellProject,
            file,
            cell,
            index,
            System.currentTimeMillis(),
        )
    }

    fun createUnboundCallback(
        project: Project,
        virtualFile: BackedNotebookVirtualFile
    ): JupyterExecutionCallback {
        val index = registerNextIndexForCallback(project, virtualFile, null)
        return KotlinNotebookCellExecutionCallback(
            project,
            virtualFile,
            null,
            index,
            System.currentTimeMillis(),
        )
    }

    companion object {
        fun getInstance() = JupyterCellExecutionCallbackFactory.EP_NAME.findExtensionOrFail(KotlinNotebookCellExecutionCallbackFactory::class.java)
    }
}
