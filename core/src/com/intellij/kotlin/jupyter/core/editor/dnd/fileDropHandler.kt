// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project

private fun isDataFrameInClasspath(notebookFile: BackedNotebookVirtualFile, project: Project): Boolean {
    val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
    return compilerService.currentClasspath.any { file ->
        file.name.startsWith("dataframe-core")
    }
}

internal fun generateCode(
    importExpression: String,
    dfName: String,
    notebookFile: BackedNotebookVirtualFile,
    project: Project,
    fileIndex: Int
): String =
    listOfNotNull(
        "%use dataframe\n".takeIf { fileIndex == 0 && !isDataFrameInClasspath(notebookFile, project) },
        """
        val $dfName = $importExpression
        $dfName
        """.trimIndent()
    ).joinToString("")