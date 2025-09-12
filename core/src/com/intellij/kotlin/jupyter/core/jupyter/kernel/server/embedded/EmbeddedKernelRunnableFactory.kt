// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowCoordinatorRegistry
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode

/**
 * Factory for creating Kotlin kernels that will run a shared process together with the
 * [JupyterClient] and the [JupyterServer].
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

        KernelProcessToolWindowCoordinatorRegistry.getOrCreate(startupOptions.project, startupOptions.notebookVirtualFile)
            .onStarted(runnableHandler)

        return runnableHandler
    }
}
