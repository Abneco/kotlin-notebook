// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.executor.kernel.JupyterKernelTask
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.kernel.executor.JupyterTaskBaseCallback
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.kotlin.jupyter.core.settings.SessionOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.generateSnippet
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookSession
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger

class JupyterKotlinExecutionServiceListener : JupyterExecutionListener {
    override suspend fun sessionCreated(session: JupyterNotebookSession) {
        if (!session.isKotlinNotebookSession()) return

        JupyterKotlinProjectArtifactsService.getInstance(session.project).registerSession(session)

        val project = session.project
        val virtualFile = session.virtualFile

        val callbacks = listOf(
            kotlinNotebookCellExecutionCallbackFactory.createUnboundCallback(project, virtualFile),
            object : JupyterTaskBaseCallback() {
                override fun onExecuteReply(message: JupyterMessage) {
                    logger<JupyterKotlinExecutionServiceListener>()
                        .debug("Kotlin session has been initialized with response: ${message.json}")
                }
            }
        )

        val task = JupyterKernelTask(
            source = service<SessionOptionsProvider>().generateSnippet(),
            options = JupyterKernelTask.Options.silentExecution(),
            callbacks = callbacks,
            notebookVirtualFile = virtualFile,
            project = project
        )
        
        JupyterExecutionManager
            .getInstanceOrCreate(project, virtualFile)
            .submitTask(task)
    }
}