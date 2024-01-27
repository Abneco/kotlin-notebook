// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.kotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import java.nio.file.Path

class EmbeddedKernelRunnableFactory : KernelRunnableFactory {
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path
    ): KotlinKernelRunnableHandler? {
        if (project.kotlinNotebookSessionRunMode != KotlinNotebookSessionRunMode.IDE_PROCESS) return null

        return EmbeddedKernelRunnableHandler(project, kernelId, notebookPath)
    }
}
