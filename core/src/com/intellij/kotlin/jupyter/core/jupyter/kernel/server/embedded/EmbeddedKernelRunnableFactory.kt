// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowManager
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
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
        replCompilerMode: ReplCompilerMode,
    ): KotlinKernelRunnableHandler {
        val runnableHandler = EmbeddedKernelRunnableHandler(
            project, kernelId, notebookPath, notebookVirtualFile, replCompilerMode
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
