// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.kotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelProcessFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowManager
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer
import java.nio.file.Path

/**
 * Factory for creating Kotlin kernels that will run a shared process together with the
 * [JupyterClient] and the [JupyterServer].
 *
 * For kernels running in separate processes, see [KernelProcessFactory].
 */
class EmbeddedKernelRunnableFactory : KernelRunnableFactory {
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
    ): KotlinKernelRunnableHandler? {
        if (project.kotlinNotebookSessionRunMode != KotlinNotebookSessionRunMode.IDE_PROCESS) return null

        val runnableHandler = EmbeddedKernelRunnableHandler(
            project, kernelId, notebookPath
        )

        KotlinNotebookToolWindowManager.getInstance(project)
            .showKotlinNotebookServerManagementToolWindow(
                EmbeddedProcessToolWindow(
                    runnableHandler
                )
            )

        return runnableHandler
    }
}
