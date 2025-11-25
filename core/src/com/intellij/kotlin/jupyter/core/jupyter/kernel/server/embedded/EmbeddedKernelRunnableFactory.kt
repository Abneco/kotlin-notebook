// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.client.JupyterClientManager
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.jupyter.execution.listeners.KernelListener
import com.intellij.jupyter.execution.listeners.events.NotebookKernelEvent
import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowCoordinatorService
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode

/**
 * Factory for creating Kotlin kernels that will run a shared process together with the
 * [JupyterClientManager] and the [JupyterServer].
 *
 * For kernels running in separate processes, see [KernelProcessFactory].
 */
class EmbeddedKernelRunnableFactory : ModeAwareKernelRunnableFactory(
    KotlinNotebookSessionRunMode.IDE_PROCESS
) {
    override fun createSpecificKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KernelRunnableHandler {
        val runnableHandler = EmbeddedKernelRunnableHandler(startupOptions)

        KernelProcessToolWindowCoordinatorService.getInstance(startupOptions.project)
            .getOrCreate(startupOptions.notebookVirtualFile)?.onStarted(runnableHandler)

        return runnableHandler.also(
            ::addKernelListener
        )
    }

    private fun addKernelListener(handler: KernelRunnableHandler) {
        handler.addBaseKernelListener(object : KernelListener {
            override fun kernelWillTerminate(event: NotebookKernelEvent) {
                val notebookVirtualFile = event.kernelsProcessHandler.notebookVirtualFile

                KernelProcessToolWindowCoordinatorService.getInstance(handler.project)
                    .get(notebookVirtualFile)?.onWillTerminate(event)
            }
        })
    }
}
