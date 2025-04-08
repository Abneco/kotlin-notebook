// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

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
     * Was this kernel ever marked as verified?
     */
    val isVerified: Boolean

    /**
     * This method is triggered only once for each created session in the very early stage of the
     * session lifecycle, before any other messages could be sent.
     * [message] is a [KERNEL_INFO_REPLY] message that may contain useful metadata.
     */
    fun onKernelInfoReply(message: JupyterMessage) {}

    /**
     * Marks the kernel as verified.
     *
     * Changes the kernel state from [KernelState.STARTED_UNVERIFIED] to [KernelState.STARTED_VERIFIED].
     * Ensures that the transition occurs only if the current state is [KernelState.STARTED_UNVERIFIED].
     * This function should be called once the kernel is verified and ready to process tasks.
     * This function is called _after_ [onKernelInfoReply] which in fact verifies kernel's responsiveness.
     */
    fun markVerified()

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
    fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession?

    /**
     * Checks if the kernel can be stopped based on the current kernel state.
     *
     * @return `true` if the kernel can be stopped, `false` otherwise.
     */
    fun canStopKernel(): Boolean = kernelState.canBeStopped
}

