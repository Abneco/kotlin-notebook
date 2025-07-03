// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Extension point for choosing how to run the Kotlin kernel for a given notebook.
 *
 * @see [com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory]
 * @see [com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded.EmbeddedKernelRunnableFactory]
 */
interface KernelRunnableFactory {

    @RequiresBackgroundThread
    fun createKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KotlinKernelRunnableHandler?

    companion object {
        val EP: ExtensionPointName<KernelRunnableFactory> =
            ExtensionPointName.create<KernelRunnableFactory>("com.intellij.kotlin.jupyter.core.kernel.kernelRunnableFactory")

        fun createKernelRunnableHandler(
            startupOptions: KernelStartupOptions,
        ): KotlinKernelRunnableHandler {
            return EP.extensionList.firstNotNullOfOrNull {
                it.createKernelRunnableHandler(startupOptions)
            } ?: error("Suitable runnable handler for ${startupOptions.notebookPath} was not found")
        }
    }
}
