// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.util

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.io.File


data class NotebookSessionData(
    val sourceRoots: List<File>,
    val classPath: List<File>,
    val scripts: List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>>,
    val executedCellsCount: Int
)

suspend fun BackedNotebookVirtualFile.getNotebookSessionData(project: Project): NotebookSessionData {
    val compilerService = JupyterCompilerService.getForFile(project, this)

    return readAction {
        val scripts = compilerService.scripts()
        NotebookSessionData(
            compilerService.currentSourceRoots,
            compilerService.currentClasspath,
            scripts,
            compilerService.executedCellsCount
        )
    }

}