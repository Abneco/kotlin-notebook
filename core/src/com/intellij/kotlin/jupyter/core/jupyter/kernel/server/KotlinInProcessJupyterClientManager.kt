// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.filecontentsapi.CachingFileContentsApi
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelBase
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.jupyter.execution.process.InProcessJupyterClientManager
import com.intellij.jupyter.execution.process.KernelName
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.cleanupKernelSession
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec

/**
 * Jupyter client that is running in the IDE process.
 */
@Service(Service.Level.PROJECT)
class KotlinInProcessJupyterClientManager : InProcessJupyterClientManager() {
    override val kernelSpecs: Map<KernelName, JupyterKernelSpec> = kotlinKernelSpecs

    override val fileContentsApi: CachingFileContentsApi
        get() = error("Kotlin is not support file contents")

    override suspend fun clearSessionAndRuntimeImpl(kernelHandler: KernelRunnableHandler) {
        if (removeAndDisposeSession(kernelHandler.kernelId)) {
            cleanupKernelSession(kernelHandler)
        }
    }

    companion object {
        private val kotlinKernelSpec = JupyterKernelBase(
            notebookKernelSpec.displayName,
            notebookKernelSpec.language,
            notebookKernelSpec.name
        )

        private val kotlinKernelSpecs: Map<KernelName, JupyterKernelSpec> = mapOf(
            DEFAULT_KOTLIN_KERNEL_NAME to kotlinKernelSpec
        )

        fun getInstance(project: Project): KotlinInProcessJupyterClientManager =
            project.getService(KotlinInProcessJupyterClientManager::class.java)
    }
}