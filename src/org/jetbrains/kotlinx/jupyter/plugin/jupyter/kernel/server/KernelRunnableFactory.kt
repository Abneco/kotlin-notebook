// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.settings.DEFAULT
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.isKernelProcessEmbeddingEnabled
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import java.nio.file.Path

interface KernelRunnableFactory {
    fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
    ): KotlinKernelRunnableHandler?

    companion object {
        val EP = ExtensionPointName.create<KernelRunnableFactory>("org.jetbrains.kotlinx.jupyter.plugin.kernel.kernelRunnableFactory")

        fun createKernelRunnableHandler(
            project: Project,
            kernelId: JupyterKernelId,
            notebookPath: Path,
        ): KotlinKernelRunnableHandler {
            return EP.extensionList.firstNotNullOfOrNull {
                it.createKernelRunnableHandler(project, kernelId, notebookPath)
            } ?: error("Suitable runnable handler for $notebookPath was not found")
        }
    }
}

val Project.kotlinNotebookSessionRunMode : KotlinNotebookSessionRunMode
    get() {
        return if (isKernelProcessEmbeddingEnabled) {
            KotlinNotebookProjectOptionsProvider.getInstance(this).kernelRunMode
        } else {
            KotlinNotebookSessionRunMode.DEFAULT
        }
    }
