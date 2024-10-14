// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.execution.NotebookPathProvider
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServer
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class KotlinNotebookPathProvider: NotebookPathProvider {
    override fun getNotebookPath(project: Project, file: VirtualFile, server: JupyterServer, kernelName: String?): String? {
        if (!isKotlinKernelName(kernelName)) return null

        return file.path
    }
}
