// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.execution.core.ManagedJupyterServerRunner
import com.intellij.jupyter.core.jupyter.connections.settings.config.JupyterManagedServerConfig
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerExecution
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ManagedKotlinNotebookServerRunner : ManagedJupyterServerRunner {
    override suspend fun startServer(
        project: Project,
        virtualFile: VirtualFile,
        kernelName: String?,
        settings: JupyterManagedServerConfig
    ): JupyterServerExecution? {
        if (isKotlinKernelName(kernelName) || virtualFile.isKotlinNotebook) {
            return KotlinNotebookServerExecution()
        }
        return null
    }
}