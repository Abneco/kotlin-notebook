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

/**
 * Abstract base class for handlers that manage the lifecycle and event handling of Kotlin Jupyter kernels.
 * Managing state and events is common for all [KotlinKernelRunnableHandler]s, so these things are extracted
 * to the abstract class.
 *
 * @param ListenerT The type of the listener that handles kernel events.
 *                  Implementations may provide additional events to listen
 * @param listenerClass The class of the listener type.
 * @param project The current project in which kernel session is created.
 * @param kernelId Generated identifier of the corresponding Jupyter kernel.
 * @param notebookPath The file path of the notebook.
 * @param notebookVirtualFile The virtual file of the notebook, if available.
 */
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

    /**
     * Converts a given [KotlinKernelListener] instance to a [ListenerT] instance.
     * This method should be implemented for all classes for which [ListenerT]
     * is NOT the base [KotlinKernelListener].
     */
    open fun convertBaseListener(listener: KotlinKernelListener): ListenerT {
        return listenerClass.cast(listener)
    }

    final override fun addBaseKernelListener(listener: KotlinKernelListener) {
        addKernelListener(convertBaseListener(listener))
    }

    /**
     * Adds a listener to the kernel event dispatcher.
     * Opposed to [addBaseKernelListener], this method works with the kernel type-specific
     * listeners.
     *
     * @param listener An instance of ListenerT to be added.
     */
    fun addKernelListener(listener: ListenerT) {
        eventDispatcher.addListener(listener)
    }
}
