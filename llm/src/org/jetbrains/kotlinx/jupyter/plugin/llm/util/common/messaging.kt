// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.util.common

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage


suspend fun BackedNotebookVirtualFile.executeCodeAndWaitForResult(project: Project, codeToExecute: String): JupyterMessage? {
    val jupyterService = JupyterRuntimeService.getInstance(project).getOrCreateSession(this)
    val result = CompletableDeferred<Unit>()
    var answer: JupyterMessage? = null

    jupyterService.execute(
        codeToExecute,
        onMessageCreated = {},
        callbacks = listOf(object : JupyterExecutionCallbackAdapter() {
            override fun onStatus(message: JupyterStatusMessage) {
                when (message.executionState) {
                    JupyterExecutionState.IDLE -> {
                        result.complete(Unit)
                        finalizeCallback()
                    }
                    JupyterExecutionState.ERROR -> {
                        result.complete(Unit)
                        finalizeCallback()
                    }
                    else -> Unit
                }
            }

            override fun onUpdateOutput(message: JupyterMessage) {
                answer = message
            }
        }),
        silent = true
    )
    result.await()

    return answer
}