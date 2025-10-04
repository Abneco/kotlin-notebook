// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelBase
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import com.intellij.jupyter.execution.process.SeparateJupyterKernelClient
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec

/**
 * Jupyter client that is running in the IDE process.
 */
class KotlinInProcessJupyterClient : SeparateJupyterKernelClient() {
    override val kernelSpec: JupyterKernelSpec
        get() = kotlinKernelSpec

    companion object {
        private val kotlinKernelSpec = JupyterKernelBase(
            notebookKernelSpec.displayName,
            notebookKernelSpec.language,
            notebookKernelSpec.name
        )

    }
}