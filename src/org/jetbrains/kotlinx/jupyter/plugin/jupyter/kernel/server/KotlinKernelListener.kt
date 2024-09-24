// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import java.util.*

interface KotlinKernelListener : EventListener {
    fun kernelInfoReplyReceived(event: KernelInfoReplyReceivedEvent) {}
    fun kernelWillTerminate(event: KotlinKernelEvent) {}
    fun kernelTerminated(event: KotlinKernelEvent) {}
}

interface KotlinKernelEvent {
    val source: KotlinKernelRunnableHandler
}

interface KernelInfoReplyReceivedEvent : KotlinKernelEvent {
    val message: JupyterMessage
}

class KernelInfoReplyReceivedEventImpl(
    override val source: KotlinKernelRunnableHandler,
    override val message: JupyterMessage,
) : KernelInfoReplyReceivedEvent
