// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.notebook.path.NotebookPathProvider
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class KotlinNotebookPathProvider: NotebookPathProvider {
    override suspend fun getNotebookPath(project: Project, file: VirtualFile, server: JupyterServer): String? {
        if (!isKotlinKernelName(server.connectionParameters.serverType)) return null

        return file.path
    }
}
