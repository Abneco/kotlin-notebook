// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageType.KERNEL_INFO_REPLY
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Interface representing a handler for managing the lifecycle and state of a Kotlin Jupyter kernel.
 *
 * This handler provides methods to control and monitor a kernel session, including starting,
 * stopping, and receiving information about the kernel's state.
 * It also holds information about kernel identifier and corresponding notebook file.
 */
interface KotlinKernelRunnableHandler: Disposable {
    val project: Project
    val kernelId: JupyterKernelId
    val notebookPath: Path
    val notebookVirtualFile: BackedNotebookVirtualFile?
    val kernelState: KernelState

    /**
     * This method is triggered only once for each created session in the very early state of the
     * session lifecycle, before any messages could be sent.
     * [message] is a [KERNEL_INFO_REPLY] message that may contain useful metadata.
     */
    fun onKernelInfoReply(message: JupyterMessage) {}

    /**
     * Marks the kernel's state as started.
     *
     * Changes the kernel state from `STARTING` to `STARTED`.
     * Ensures that the transition occurs only if the current state is `STARTING`.
     * This function should be called once the kernel is fully started and ready to process tasks.
     * This function is called _after_ [onKernelInfoReply]
     */
    fun markStarted()

    /**
     * Adds a listener for the events that happen with every [KotlinKernelRunnableHandler]
     */
    fun addBaseKernelListener(listener: KotlinKernelListener)

    /**
     * Creates a new session for a Jupyter notebook.
     *
     * @param sessionId The unique identifier for the Jupyter notebook session.
     * @param onMessage A callback function to handle incoming Jupyter messages.
     * @return A new instance of `KotlinKernelSession` representing the created session.
     */
    fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession

    /**
     * Checks if the kernel can be stopped based on the current kernel state.
     *
     * @return `true` if the kernel can be stopped, `false` otherwise.
     */
    fun canStopKernel(): Boolean = kernelState.canBeStopped

    /**
     * Stops the kernel if it is currently in a state that allows stopping
     * (either [KernelState.STARTING] or [KernelState.STARTED]).
     *
     * This method transitions the kernel's state to [KernelState.TERMINATED].
     *
     * It may and should have side effects like process termination and inability to
     * send new messages
     */
    fun stopKernel()
}

