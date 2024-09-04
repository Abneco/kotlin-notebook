// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.EventDispatcher
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelState
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelStateMachine
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

class EmbeddedKernelRunnableHandler(
  override val project: Project,
  override val kernelId: JupyterKernelId,
  override val notebookPath: Path,
) : KotlinKernelRunnableHandler {

    val loggerFactory: EmbeddedKotlinKernelLoggerFactory = EmbeddedKotlinKernelLoggerFactory()

    override val notebookVirtualFile by lazy {
        val file = VirtualFileManager.getInstance().findFileByNioPath(notebookPath) ?: return@lazy null
        BackedNotebookVirtualFile.find(file)
    }

    private val stateMachine = KernelStateMachine()
    override val kernelState: KernelState get() = stateMachine.currentState

    override fun markStarted() {
        stateMachine.started()
    }

    private val eventDispatcher = EventDispatcher.create(KotlinKernelListener::class.java)

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

    override fun addKernelListener(listener: KotlinKernelListener) {
        eventDispatcher.listeners.add(listener)
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return EmbeddedKotlinKernelSession(project, sessionId, notebookPath, loggerFactory, onMessage)
    }
}
