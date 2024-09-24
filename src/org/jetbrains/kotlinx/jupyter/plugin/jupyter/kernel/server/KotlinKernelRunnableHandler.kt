// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

interface KotlinKernelRunnableHandler: Disposable {
    val project: Project
    val kernelId: JupyterKernelId
    val notebookPath: Path
    val notebookVirtualFile: BackedNotebookVirtualFile?
    val kernelState: KernelState

    fun onKernelInfoReply(message: JupyterMessage) {}

    fun addKernelListener(listener: KotlinKernelListener)

    fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession

    fun canStopKernel(): Boolean = kernelState.canBeStopped

    fun stopKernel()

    fun markStarted()
}

