// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path

/**
 * Extension point for choosing how to run the Kotlin kernel for a given notebook.
 *
 * @see [com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory]
 * @see [com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded.EmbeddedKernelRunnableFactory]
 */
interface KernelRunnableFactory {

    @RequiresBackgroundThread
    fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
    ): KotlinKernelRunnableHandler?

    companion object {
        val EP = ExtensionPointName.create<KernelRunnableFactory>("com.intellij.kotlin.jupyter.core.kernel.kernelRunnableFactory")

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
