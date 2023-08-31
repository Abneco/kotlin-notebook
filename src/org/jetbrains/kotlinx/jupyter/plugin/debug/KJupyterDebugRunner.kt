// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug


import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KJupyterDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.SessionRelatedInfo
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.updateInfoBeforeExecution
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.NotebookPathProvider
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.ui.JupyterErrorReporter
import org.jetbrains.plugins.notebooks.jupyter.debugger.DebugConnectionNotifier
import org.jetbrains.plugins.notebooks.jupyter.debugger.OutputConsumer
import org.jetbrains.plugins.notebooks.jupyter.debugger.common.NotebookDebugRunner
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import java.util.concurrent.TimeUnit


class KJupyterDebugRunner(project: Project, private val virtualFile: BackedNotebookVirtualFile) : NotebookDebugRunner {
    private var myDebugSession: DebuggerSession? = null
    private var currentNotebookSession: JupyterNotebookSession = runBlocking {
        JupyterRuntimeService.getInstance(project).getOrCreateSession(virtualFile)
    }

    private val artifactsService = JupyterKotlinProjectArtifactsService.getInstance(project)
    private val coroutineScope = CoroutineScope(Job())

    override fun createDebugSession(
        project: Project,
        cell: JupyterPsiCell,
        cellPointer: NotebookIntervalPointer,
        connectionNotifier: DebugConnectionNotifier,
        output: OutputConsumer?,
        afterCellExecuted: () -> Unit
    ): XDebugSession? {
        val debugSessionManager = KJupyterDebugSessionManager.getForFile(project, virtualFile)

        val sessionRelatedInfo = SessionRelatedInfo(
            project, virtualFile, 1
        )
        currentNotebookSession = JupyterRuntimeService.getInstance(project).getSession(sessionRelatedInfo.myNotebook.file)!!
        val sessionPath = NotebookPathProvider.calculateNotebookPath(project, virtualFile.file, notebookKernelSpec.name)
        sessionRelatedInfo.updateWith(
          project,
          debugSessionManager.targetDebugPort,
          cell, cellPointer, cell.toFileName(),
          sessionPath
        )

        if (sessionRelatedInfo.debugPort == null) {
            sessionRelatedInfo.debugPort = 1044
        }

        coroutineScope.async {
            artifactsService.buildProjectAndGetLibraries(sessionRelatedInfo.myNotebook)
        }

        myDebugSession = debugSessionManager.connectToKernelVirtualMachine(
            project,
            sessionRelatedInfo.debugPort!!,
            forceRestart = true,
            silent = false
        )

        // see NotebookEditorRunActionsHandler
        cell.updateInfoBeforeExecution(project, sessionRelatedInfo.myNotebook, cellPointer.get()?.ordinal)

        runInEdt {
            FileDocumentManager.getInstance().saveAllDocuments()

            AppExecutorUtil.getAppScheduledExecutorService().schedule(
                {
                    runInEdt {
                        executeCell(project, cell, sessionRelatedInfo)
                    }
                }, 1600, TimeUnit.MILLISECONDS)
        }

        //return XDebuggerManager.getInstance(project).currentSession
        return myDebugSession?.xDebugSession
    }

    override val connectionNotifier: DebugConnectionNotifier
        get() = DebugConnectionNotifier.DoNothing

    override fun getRunnerId(): String = runnerName

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (DefaultDebugExecutor.EXECUTOR_ID != executorId) {
            // If not debug at all
            return false
        }
        return true
    }

    override fun execute(environment: ExecutionEnvironment) {
    }


    // @see JupyterKernelClient#execute
    // @see JupyterCellExecutionManager executeCode
    private fun executeCell(project: Project, cell: JupyterPsiCell, sessionRelatedInfo: SessionRelatedInfo) {
        FileDocumentManager.getInstance().saveAllDocuments()
        // see convenience methods in obj of JupyterExecutionTask
        val sessionOptions = JupyterExecutionTask.Options.cellExecution(sessionRelatedInfo.cellPointer!!)
        project.run {
            try {
/*                val breakPointManager = KJupyterDebugSessionManager.getBreakpointManagerForFile(project, sessionRelatedInfo.myNotebook)
                breakPointManager.lastExecutedCell = cell*/

                JupyterCellExecutionManager.getInstance(this).submitTask(JupyterExecutionTask(
                    source = cell.text,
                    options = sessionOptions,
                    callbacks = emptyList(),
                    onError = { e ->
                        JupyterErrorReporter.displayAndLogError(this, e)
                        //breakPointManager.restorePreviousLastCell()
                    },
                    notebookVirtualFile = sessionRelatedInfo.myNotebook,
                    project = project))
            }
            catch (e: Exception) {
                JupyterErrorReporter.displayAndLogError(this, e)
            }
        }

    }

    companion object {
        const val runnerName = "KJupyter Debug Runner"

        val LOG = logger<KJupyterDebugRunner>()

        private fun JupyterPsiCell.toFileName(): @NlsSafe String {
            return if (this.containingFile != null) {
                "KTNB_${FileUtil.getNameWithoutExtension(this.containingFile.name)}"
            } else {
                "KTNB_File"
            }
        }
    }
}