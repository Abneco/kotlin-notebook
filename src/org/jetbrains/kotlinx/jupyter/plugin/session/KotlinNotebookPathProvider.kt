// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.NotebookPathProvider

class KotlinNotebookPathProvider: NotebookPathProvider {
    override fun getNotebookPath(project: Project, file: VirtualFile, kernelName: String?): String? {
        if (kernelName != "kotlin") return null

        return file.path
    }
}