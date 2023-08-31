// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.debugger.common.JupyterDebugRunnerProvider
import org.jetbrains.plugins.notebooks.jupyter.debugger.common.NotebookDebugRunner

class JupyterKotlinDebugRunnerProvider : JupyterDebugRunnerProvider {

    override fun isSuitable(sdk: Sdk?, virtualFile: BackedNotebookVirtualFile): Boolean = virtualFile.file.isKotlinNotebook

    override fun getDebugRunner(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookDebugRunner {
        return KJupyterDebugRunner(project, virtualFile)
    }
}