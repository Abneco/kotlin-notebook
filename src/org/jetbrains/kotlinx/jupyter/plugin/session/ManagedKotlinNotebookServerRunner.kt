// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.connections.ManagedServerJupyterModuleConnectionSettings
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.ManagedJupyterServerRunner
import org.jetbrains.plugins.notebooks.jupyter.server.JupyterServerExecution

class ManagedKotlinNotebookServerRunner : ManagedJupyterServerRunner {
    override fun startServer(
        project: Project,
        virtualFile: VirtualFile,
        kernelName: String?,
        settings: ManagedServerJupyterModuleConnectionSettings?
    ): JupyterServerExecution? {
        if (kernelName == "kotlin" || virtualFile.isKotlinNotebook) {
            return KotlinNotebookServerExecution()
        }
        return null
    }
}