// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.util.findNotebookVirtualFileOrNull
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import java.nio.file.Path

/**
 * Options that are used to start a kernel.
 * Caching aka `lazy` ensures that the options are not changed during the kernel startup.
 */
class KernelStartupOptions(
    val project: Project,
    val kernelId: JupyterKernelId,
    val notebookPath: Path,
) {
    val notebookVirtualFile: BackedNotebookVirtualFile? by lazy {
        notebookPath.findNotebookVirtualFileOrNull()
    }

    val replCompilerMode: ReplCompilerMode by lazy {
        KotlinNotebookApplicationOptions.get().replCompilerMode
    }

    val extraCompilerArguments: List<String> by lazy {
        KotlinNotebookProjectOptionsProvider.getInstance(project).extraCompilerArguments.toList()
    }
}
