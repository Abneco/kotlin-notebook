// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.getSessionRunMode

/**
 * Implementation of [KernelRunnableFactory] that only creates [KotlinKernelRunnableHandler] if
 * the current mode is equal to [mode].
 * Current mode is taken from the notebook settings.
 */
abstract class ModeAwareKernelRunnableFactory(
    private val mode: KotlinNotebookSessionRunMode,
) : KernelRunnableFactory {
    final override fun createKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KotlinKernelRunnableHandler? {
        val notebookVirtualFile = startupOptions.notebookVirtualFile

        val currentMode = notebookVirtualFile?.getSessionRunMode(startupOptions.project)
        if (currentMode != mode) return null

        return createSpecificKernelRunnableHandler(startupOptions)
    }

    protected abstract fun createSpecificKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KotlinKernelRunnableHandler
}