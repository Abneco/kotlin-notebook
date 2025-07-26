// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.UpdateState
import com.intellij.kotlin.jupyter.test.ScriptingUpdateMode.FileAgnostic
import com.intellij.kotlin.jupyter.test.ScriptingUpdateMode.NotebookFileFocused
import com.intellij.kotlin.jupyter.test.scripting.PostScriptingUpdateKotlinModeAwareHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Options to invoke [TestNotebookScriptsDependenciesUpdater.setUpDependenciesSynchronously] with.
 * [NotebookFileFocused] should be used when a notebook file is opened in the Editor,
 * while [FileAgnostic] targets no file, but a project.
 */
enum class ScriptingUpdateMode {
    NotebookFileFocused,
    FileAgnostic
}

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
    private val notebookFile: BackedNotebookVirtualFile?,
    private val cellsToExecute: Int,
    parentDisposable: Disposable,
    private val scriptingUpdateWaitTimeout: Duration = 3.minutes,
) {
    init {
        project.messageBus.connect(parentDisposable).subscribe(
            NotebookScriptsStateListener.TOPIC,
            ScriptingUpdateListener()
        )

        JupyterCompilerService.getInstance(project).requestScriptingUpdate()
    }

    // We do not fully control when this flow is subscribed to, so make sure to cache all values.
    private val scriptsUpdateCompleted = MutableSharedFlow<Boolean>(replay = Int.MAX_VALUE)
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
            val vFile = notebookFile
            if (vFile != null && file != vFile) {
                return
            }

            when (updateState) {
                UpdateState.COMPLETE -> updateCompleted()
                UpdateState.INCOMPLETE, UpdateState.SKIPPED -> updateNotCompleted()
            }
        }

        private fun updateCompleted() {
            val counter = scriptingUpdatesLeft.getAndDecrement()
            if (counter <= 0) {
                return
            }

            if (counter == 1) { // it's the last update, emit signal
                if (!scriptsUpdateCompleted.tryEmit(true)) {
                    throw IllegalStateException("Cannot emit to scriptsUpdateCompleted")
                }
            } else {
                JupyterCompilerService.getInstance(project).requestScriptingUpdate()
            }
        }

        private fun updateNotCompleted() {
            if (!scriptsUpdateCompleted.tryEmit(false)) {
                throw IllegalStateException("Cannot emit to scriptsUpdateCompleted")
            }
            scriptingUpdatesLeft.incrementAndGet()
        }
    }

    /**
     * Performs scripting updates and waits for their completion.
     */
    suspend fun setUpDependenciesSynchronously(testFixture: CodeInsightTestFixture) {
        try {
            scriptingUpdatesLeft.set(cellsToExecute)

            // Loop until all updates are completed
            withTimeout(scriptingUpdateWaitTimeout) {
                scriptsUpdateCompleted
                    .takeWhile { scriptUpdated: Boolean -> !scriptUpdated }
                    .collect {
                        LOG.debug("Dependency update is not completed yet. Waiting for next update")
                    }
            }

            // Index is up to date, invoke post-handler
            withContext(Dispatchers.EDT) {
                PostScriptingUpdateKotlinModeAwareHandler.afterScriptingUpdate(testFixture)
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