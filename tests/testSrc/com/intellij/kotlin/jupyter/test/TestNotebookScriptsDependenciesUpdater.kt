// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.UpdateState
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger


/**
 * Main class for updating scripting using our update scheduling logic
 * for both K1 and K2 modes.
 *
 * [cellsToExecute] is the minimum estimate of updates needed, given that very first one
 * is for initial dependencies
 *
 * Use this class if [junit.framework.TestCase] requires any scripting dependencies.
 */
class TestNotebookScriptsDependenciesUpdater(
    private val project: Project,
    private val notebookFile: BackedNotebookVirtualFile,
    parentDisposable: Disposable
) {
    init {
        project.messageBus.connect(parentDisposable).subscribe(
            NotebookScriptsStateListener.TOPIC,
            ScriptingUpdateListener()
        )
    }

    private val scriptsUpdateCompleted = MutableStateFlow(false)
    private val scriptingUpdatesLeft = AtomicInteger(cellsToExecute)

    /**
     * Processes events about scripts changes after updates.
     * Each [UpdateState.COMPLETE] update decrement needed [scriptingUpdatesLeft] counter.
     * If update was [UpdateState.INCOMPLETE], yet new iteration is required.
     */
    inner class ScriptingUpdateListener() : NotebookScriptsStateListener {
        override fun scriptsConfigurationUpdated(
            file: BackedNotebookVirtualFile,
            updateState: UpdateState
        ) {
            if (file != notebookFile) {
                return
            }

            when (updateState) {
                UpdateState.COMPLETE -> updateCompleted()
                UpdateState.INCOMPLETE -> updateNotCompleted()
            }
        }

        private fun updateCompleted() {
            if (scriptsUpdateCompleted.compareAndSet(false, true)) {
                scriptingUpdatesLeft.decrementAndGet()
                LOG.debug("Scripts changed for file $notebookFile, releasing lock")
            }
        }

        private fun updateNotCompleted() {
            if (scriptsUpdateCompleted.value) {
                scriptingUpdatesLeft.incrementAndGet()
            }
        }
    }

    /**
     * Performs scripting updates and waits for their completion.
     */
    suspend fun setUpDependenciesSynchronously() {
        try {
            val updatesFromCells = if (cellsToExecute.isNotEmpty()) 1 else 0
            scriptingUpdatesLeft.set(1 + updatesFromCells)

            // Loop until all updates are completed
            while (scriptingUpdatesLeft.get() > 0) {
                // Indicate that a new iteration of the update is pending
                scriptsUpdateCompleted.value = false

                scriptsUpdateCompleted.first { it }

                // Schedule new update request
                JupyterCompilerService.getInstance(project).requestScriptingUpdate()
            }
        } catch (ex: Exception) {
            LOG.warn("Exception while updating dependencies", ex)
            throw ex
        }
    }

    companion object {
        private val LOG = thisLogger()
    }
}