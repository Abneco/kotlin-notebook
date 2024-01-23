// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.EventDispatcher
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelState
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

class EmbeddedKernelRunnableHandler(
    override val project: Project,
    override val kernelId: JupyterKernelId,
    private val notebookPath: Path,
) : KotlinKernelRunnableHandler {

    override val notebookVirtualFile by lazy {
        val file = VirtualFileManager.getInstance().findFileByNioPath(notebookPath) ?: return@lazy null
        BackedNotebookVirtualFile.find(file)
    }

    override val kernelState: KernelState get() = KernelState.STARTED
    override fun markStarted() {}

    private val eventDispatcher = EventDispatcher.create(KotlinKernelListener::class.java)

    override fun stopKernel() {
        eventDispatcher.multicaster.kernelTerminated(
            EmbeddedKernelEvent(this)
        )
    }

    override fun canStopKernel(): Boolean {
        return true
    }

    override fun dispose() {
        stopKernel()
        eventDispatcher.listeners.clear()
    }

    override fun addKernelListener(listener: KotlinKernelListener) {
        eventDispatcher.listeners.add(listener)
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return EmbeddedKotlinKernelSession(project, sessionId, notebookPath, onMessage)
    }
}
