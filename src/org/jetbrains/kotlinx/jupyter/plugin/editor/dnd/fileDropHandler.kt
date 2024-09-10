// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService

internal fun isDataFrameInClasspath(editor: Editor): Boolean {
    val notebookFile = editor.notebookFileOrNull ?: return false
    val project = editor.project ?: return false
    val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
    return compilerService.currentClasspath.any { file ->
        file.name.startsWith("dataframe-core")
    }
}

internal fun generateCode(importExpression: String, dfName: String, editor: Editor, fileIndex: Int): List<String> = listOfNotNull(
    "%use dataframe\n".takeIf { fileIndex == 0 && !isDataFrameInClasspath(editor) },
    """
    val $dfName = $importExpression
    $dfName
    """.trimIndent()
)