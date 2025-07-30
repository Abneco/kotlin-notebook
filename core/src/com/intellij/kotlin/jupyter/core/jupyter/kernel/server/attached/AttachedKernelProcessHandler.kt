// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelStartupOptions
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.ACCEPT_ALL_MESSAGES
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelWsClientSession
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams

class AttachedKernelProcessHandler(
    startupOptions: KernelStartupOptions,
    private val jupyterParams: KernelJupyterParams,
) : AbstractKotlinKernelRunnableHandler<KotlinKernelListener>(
    KotlinKernelListener::class,
    startupOptions
) {
    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession? {
        if (!jupyterParams.areAllSocketsOpen()) {
            project.notebookNotifications.showNoKernelToAttach()
            return null
        }

        return KernelWsClientSession(
            sessionId,
            jupyterParams,
            onMessage,
            ACCEPT_ALL_MESSAGES,
        )
    }

    override fun dispose() {
        val event = AttachedKernelEvent(this)
        notifyTerminatedAndDispose(event)
    }
}
