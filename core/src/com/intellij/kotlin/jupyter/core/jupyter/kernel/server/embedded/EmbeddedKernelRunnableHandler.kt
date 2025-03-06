// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import java.nio.file.Path

class EmbeddedKernelRunnableHandler(
    project: Project,
    kernelId: JupyterKernelId,
    notebookPath: Path,
    notebookVirtualFile: BackedNotebookVirtualFile?,
    private val replCompilerMode: ReplCompilerMode,
) : AbstractKotlinKernelRunnableHandler<KotlinKernelListener>(
    KotlinKernelListener::class,
    project, kernelId, notebookPath, notebookVirtualFile
) {

    val loggerFactory: EmbeddedKotlinKernelLoggerFactory = EmbeddedKotlinKernelLoggerFactory()

    override fun stopKernel() {
        val event = EmbeddedKernelEvent(this)
        if (stateMachine.terminating()) {
            eventDispatcher.multicaster.kernelWillTerminate(event)
        }

        if (stateMachine.terminated()) {
            eventDispatcher.multicaster.kernelTerminated(event)
        }
    }

    override fun dispose() {
        stopKernel()
        eventDispatcher.listeners.clear()
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return EmbeddedKotlinKernelSession(project, sessionId, notebookPath, loggerFactory, replCompilerMode, onMessage)
    }
}
