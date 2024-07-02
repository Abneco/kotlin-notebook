// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

interface KotlinKernelRunnableHandler: Disposable {
    val project: Project
    val kernelId: JupyterKernelId
    val notebookPath: Path
    val notebookVirtualFile: BackedNotebookVirtualFile?
    val kernelState: KernelState

    fun addKernelListener(listener: KotlinKernelListener)

    fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession

    fun canStopKernel(): Boolean = kernelState.canBeStopped

    fun stopKernel()

    fun markStarted()
}

enum class KernelState {
    STARTING,
    STARTED,
    TERMINATING,
    TERMINATED,
}

val KernelState.canBeStopped: Boolean get() = equals(KernelState.STARTING) || equals(KernelState.STARTED)

class KernelStateMachine {
    private val state = MutableStateFlow(KernelState.STARTING)

    fun started(): Boolean {
        return state.compareAndSet(KernelState.STARTING, KernelState.STARTED)
    }

    fun terminating(): Boolean {
        started()
        return state.compareAndSet(KernelState.STARTED, KernelState.TERMINATING)
    }

    fun terminated(): Boolean {
        terminating()
        return state.compareAndSet(KernelState.TERMINATING, KernelState.TERMINATED)
    }

    val currentState get() = state.value
}
