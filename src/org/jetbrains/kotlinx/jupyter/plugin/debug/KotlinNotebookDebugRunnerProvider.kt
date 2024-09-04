// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugRunnerProvider
import com.intellij.jupyter.core.jupyter.debugger.common.NotebookDebugRunner

class KotlinNotebookDebugRunnerProvider : JupyterDebugRunnerProvider {
    override fun isSuitable(sdk: Sdk?, virtualFile: BackedNotebookVirtualFile): Boolean = virtualFile.file.isKotlinNotebook

    override fun getDebugRunner(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookDebugRunner {
        return KotlinNotebookDebugRunner(project, virtualFile)
    }
}