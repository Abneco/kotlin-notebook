// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage

interface KotlinKernelRunnableHandler: Disposable {
    val project: Project
    val kernelId: JupyterKernelId
    val notebookVirtualFile: BackedNotebookVirtualFile?
    val kernelState: KernelState

    fun addKernelListener(listener: KotlinKernelListener)

    fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession

    fun canStopKernel(): Boolean

    fun stopKernel()

    fun markStarted()
}

enum class KernelState {
    STARTING,
    STARTED,
}
