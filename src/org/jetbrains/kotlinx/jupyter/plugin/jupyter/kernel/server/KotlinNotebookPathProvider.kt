// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinKernelName
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.NotebookPathProvider
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer

class KotlinNotebookPathProvider: NotebookPathProvider {
    override fun getNotebookPath(project: Project, file: VirtualFile, server: JupyterServer, kernelName: String?): String? {
        if (!isKotlinKernelName(kernelName)) return null

        return file.path
    }
}
