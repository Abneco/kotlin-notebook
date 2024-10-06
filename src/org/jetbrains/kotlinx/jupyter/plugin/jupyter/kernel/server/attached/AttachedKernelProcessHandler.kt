// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.attached

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelZMQClientSession
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.nio.file.Path

class AttachedKernelProcessHandler(
    project: Project,
    kernelId: JupyterKernelId,
    notebookPath: Path,
    notebookVirtualFile: BackedNotebookVirtualFile?,
    private val kernelConfig: KernelConfig,
) : AbstractKotlinKernelRunnableHandler<KotlinKernelListener>(
    KotlinKernelListener::class,
    project, kernelId, notebookPath, notebookVirtualFile
) {
    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return KernelZMQClientSession(
            sessionId,
            kernelConfig,
            onMessage,
        )
    }

    override fun stopKernel() {
        val event = AttachedKernelEvent(this)

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
}
