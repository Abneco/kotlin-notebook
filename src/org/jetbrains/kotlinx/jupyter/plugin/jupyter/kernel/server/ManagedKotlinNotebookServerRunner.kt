// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinKernelName
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.jupyter.connections.execution.core.ManagedJupyterServerRunner
import com.intellij.jupyter.core.jupyter.connections.settings.ManagedServerJupyterModuleConnectionSettings
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerExecution

class ManagedKotlinNotebookServerRunner : ManagedJupyterServerRunner {
    override fun startServer(
        project: Project,
        virtualFile: VirtualFile,
        kernelName: String?,
        settings: ManagedServerJupyterModuleConnectionSettings?
    ): JupyterServerExecution? {
        if (isKotlinKernelName(kernelName) || virtualFile.isKotlinNotebook) {
            return KotlinNotebookServerExecution()
        }
        return null
    }
}