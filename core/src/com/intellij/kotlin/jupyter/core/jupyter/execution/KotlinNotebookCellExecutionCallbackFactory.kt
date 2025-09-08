// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.kernel.JupyterKernelTask
import com.intellij.jupyter.core.jupyter.connections.action.JupyterRestartKernelListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.helper.JupyterHelper
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.withWriteLock
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class KotlinNotebookCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {
    init {
      ApplicationManager.getApplication().messageBus.connect()
          .subscribe(JupyterRestartKernelListener.TOPIC, JupyterRestartKernelListener { notebookFile ->
              executionDataLock.withWriteLock {
                  callbacksCounters.remove(notebookFile)
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

            callbacksCounters[file] = (cnt + 1) to pq
            cnt
        }
    }

    fun unregisterCallback(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        executionDataLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write
            pq.remove(index)
            val isAfterSeriesRuns = pq.size == 1 && pq.contains(-1)
            if (isAfterSeriesRuns) pq.remove(-1)
        }
    }

    override fun create(task: JupyterKernelTask): JupyterExecutionCallback? {
        val cellProject = task.project ?: return null
        val file = task.notebookVirtualFile
        if (!file.file.isKotlinNotebook) return null

        val jupyterPsiCellData = runReadAction {
          val cellIndex = task.options.cellPointer?.get()?.ordinal ?: return@runReadAction null
          JupyterHelper.getPsiCells(cellProject, task.notebookVirtualFile)?.getOrNull(cellIndex) to cellIndex
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

}

val kotlinNotebookCellExecutionCallbackFactory: KotlinNotebookCellExecutionCallbackFactory get() {
    return JupyterCellExecutionCallbackFactory.EP_NAME
        .findExtensionOrFail(KotlinNotebookCellExecutionCallbackFactory::class.java)
}