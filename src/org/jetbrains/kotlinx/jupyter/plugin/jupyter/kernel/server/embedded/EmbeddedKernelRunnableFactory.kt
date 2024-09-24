// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServer
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelProcessFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowManager
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import java.nio.file.Path

/**
 * Factory for creating Kotlin kernels that will run a shared process together with the
 * [JupyterClient] and the [JupyterServer].
 *
 * For kernels running in separate processes, see [KernelProcessFactory].
 */
class EmbeddedKernelRunnableFactory : ModeAwareKernelRunnableFactory(
    KotlinNotebookSessionRunMode.IDE_PROCESS
) {
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
        notebookVirtualFile: BackedNotebookVirtualFile?,
    ): KotlinKernelRunnableHandler {
        val runnableHandler = EmbeddedKernelRunnableHandler(
            project, kernelId, notebookPath, notebookVirtualFile
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
