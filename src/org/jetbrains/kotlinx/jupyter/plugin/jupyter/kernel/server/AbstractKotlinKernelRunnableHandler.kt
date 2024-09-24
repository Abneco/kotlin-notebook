// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.cast

abstract class AbstractKotlinKernelRunnableHandler<ListenerT: KotlinKernelListener>(
    private val listenerClass: KClass<ListenerT>,
    override val project: Project,
    override val kernelId: JupyterKernelId,
    override val notebookPath: Path,
    override val notebookVirtualFile: BackedNotebookVirtualFile?
) : KotlinKernelRunnableHandler {
    protected val stateMachine = KernelStateMachine()
    override val kernelState: KernelState get() = stateMachine.currentState

    override fun markStarted() {
        stateMachine.started()
    }

    protected val eventDispatcher = EventDispatcher.create(listenerClass.java)

    override fun onKernelInfoReply(message: JupyterMessage) {
        val event = KernelInfoReplyReceivedEventImpl(this, message)
        eventDispatcher.multicaster.kernelInfoReplyReceived(event)
    }

    open fun convertToSpecificListener(listener: KotlinKernelListener): ListenerT {
        return listenerClass.cast(listener)
    }

    final override fun addKernelListener(listener: KotlinKernelListener) {
        addSpecificKernelListener(convertToSpecificListener(listener))
    }

    fun addSpecificKernelListener(listener: ListenerT) {
        eventDispatcher.addListener(listener)
    }
}
