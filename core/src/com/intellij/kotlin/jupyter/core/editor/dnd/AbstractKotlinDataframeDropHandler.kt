// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.handlers.DataframeVariableNameSuggester
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage

abstract class AbstractKotlinDataframeDropHandler(
    @Nls commandName: String,
    fileExtensions: Set<String>
) : LanguageTableDataFileDropHandler(
    KotlinLanguage.INSTANCE,
    commandName,
    fileExtensions
) {
    override val nameSuggester: DataframeVariableNameSuggester
        get() = KotlinDataframeVariableNameSuggester

    protected abstract fun generateImportExpression(
        dataFilePath: String,
        context: TableDataFileDropHandlerContext
    ): String

    override fun generateCellCode(context: TableDataFileDropHandlerContext): String {
        val dataFilePath = context.resolveFilePath()
        val dfName = context.dataframeName ?: nameSuggester.createDataframeName(context.projectOrNull, context.dataFileNameWithoutExtension)
        val importExpression = generateImportExpression(dataFilePath, context)

        return generateCode(importExpression, dfName, context.fileIndex, getIsDataFrameInClasspath = {
            when (val pathData = context.pathData) {
                is TableDataFileDropHandlerContext.PathData.FileBased -> isDataFrameInClasspath(pathData.notebookFile, pathData.project)
                is TableDataFileDropHandlerContext.PathData.Lightweight -> false
            }
        })
    }

    private fun isDataFrameInClasspath(notebookFile: BackedNotebookVirtualFile, project: Project): Boolean {
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return compilerService.currentClasspath.any { file ->
            file.name.startsWith("dataframe-core")
        }
    }

    private inline fun generateCode(
        importExpression: String,
        dfName: String,
        fileIndex: Int,
        getIsDataFrameInClasspath: () -> Boolean,
    ): String =
        listOfNotNull(
            "%use dataframe\n".takeIf { fileIndex == 0 && !getIsDataFrameInClasspath() },
            """
            val $dfName = $importExpression
            $dfName
            """.trimIndent()
        ).joinToString("")
}
