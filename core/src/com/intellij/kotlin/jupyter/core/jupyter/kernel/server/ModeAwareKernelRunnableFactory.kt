// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.jupyter.execution.kernel.KernelRunnableFactory
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.getSessionRunMode
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook

/**
 * Implementation of [com.intellij.jupyter.execution.kernel.KernelRunnableFactory] that only creates [com.intellij.jupyter.execution.kernel.KernelRunnableHandler] if
 * the current mode is equal to [mode].
 * Current mode is taken from the notebook settings.
 */
abstract class ModeAwareKernelRunnableFactory(
    private val mode: KotlinNotebookSessionRunMode,
) : KernelRunnableFactory {
    final override fun createKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KernelRunnableHandler? {
        if (!startupOptions.notebookVirtualFile.isKotlinNotebook) return null
        val currentMode = startupOptions.notebookVirtualFile.getSessionRunMode(startupOptions.project)
        if (currentMode != mode) return null

        return createSpecificKernelRunnableHandler(startupOptions)
    }

    protected abstract fun createSpecificKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KernelRunnableHandler
}