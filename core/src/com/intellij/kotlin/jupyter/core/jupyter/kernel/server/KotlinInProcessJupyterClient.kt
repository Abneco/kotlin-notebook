// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.filecontentsapi.CachingFileContentsApi
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelBase
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import com.intellij.jupyter.execution.process.InProcessJupyterClient
import com.intellij.jupyter.execution.process.KernelName
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.cleanupKernelSession
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec

/**
 * Jupyter client that is running in the IDE process.
 */

class KotlinInProcessJupyterClient() : InProcessJupyterClient() {
    override val kernelSpecs: Map<KernelName, JupyterKernelSpec> = Companion.kernelSpecs

    override val fileContentsApi: CachingFileContentsApi
        get() = error("Kotlin is not support file contents")

    init {
        subscribeToSessionVerified()
    }

    override suspend fun clearSessionAndRuntimeImpl(kernelHandler: KernelRunnableHandler) {
        if (removeAndDisposeSession(kernelHandler.kernelId)) {
            cleanupKernelSession(kernelHandler)
        }
    }

    override fun doAfterRestart(handler: KernelRunnableHandler) {
        handler.project.notebookNotifications.showKernelRestart()
    }

    companion object {
        private val kernelSpecs: Map<KernelName, JupyterKernelSpec> = mapOf(
            DEFAULT_KOTLIN_KERNEL_NAME to JupyterKernelBase(
                notebookKernelSpec.displayName,
                notebookKernelSpec.language,
                notebookKernelSpec.name
            )
        )
    }
}