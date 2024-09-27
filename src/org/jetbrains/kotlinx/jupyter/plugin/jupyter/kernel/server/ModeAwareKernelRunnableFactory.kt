// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.sessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.util.findNotebookVirtualFileOrNull
import java.nio.file.Path

/**
 * Implementation of [KernelRunnableFactory] that only creates [KotlinKernelRunnableHandler] if
 * the current mode is equal to [mode].
 * Current mode is taken from the notebook settings.
 */
abstract class ModeAwareKernelRunnableFactory(
    private val mode: KotlinNotebookSessionRunMode,
) : KernelRunnableFactory {
    final override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path
    ): KotlinKernelRunnableHandler? {
        val notebookVirtualFile = notebookPath.findNotebookVirtualFileOrNull()

        val currentMode = notebookVirtualFile?.notebook?.sessionRunMode
        if (currentMode != mode) return null

        return createKernelRunnableHandler(project, kernelId, notebookPath, notebookVirtualFile)
    }

    protected abstract fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
        notebookVirtualFile: BackedNotebookVirtualFile?,
    ): KotlinKernelRunnableHandler
}