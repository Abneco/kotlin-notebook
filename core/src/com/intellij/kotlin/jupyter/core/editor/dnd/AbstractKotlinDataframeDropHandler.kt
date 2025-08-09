// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.handlers.DataInputCodeGenerationContext
import com.intellij.jupyter.core.editor.handlers.DataframeVariableNameSuggester
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.nio.file.Path
import kotlin.io.path.name

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

    protected open fun isTargetLibrary(file: Path): Boolean {
        return file.name.startsWith("dataframe-core")
    }

    protected open fun getUseStatement(): String {
        return "%use dataframe\n"
    }

    protected open fun getCellResultStatement(variableName: String): String {
        return variableName
    }

    override fun generateCellCode(context: TableDataFileDropHandlerContext): String {
        val dataFilePath = context.resolveFilePath()
        val dfName = context.dataframeName ?: nameSuggester.createDataframeName(context.projectOrNull, context.dataFileNameWithoutExtension)
        val importExpression = generateImportExpression(dataFilePath, context)
        return generateCode(importExpression, dfName, context.shouldGenerateUseStatement())
    }

    private fun TableDataFileDropHandlerContext.shouldGenerateUseStatement(): Boolean {
        if (generationContext.shouldAlwaysGenerateUseStatement()) return true
        if (fileIndex != 0) return false
        return when (val pathData = pathData) {
            is TableDataFileDropHandlerContext.PathData.FileBased -> !isDataFrameInClasspath(
                pathData.notebookFile,
                pathData.project,
            )
            is TableDataFileDropHandlerContext.PathData.Lightweight -> true
        }
    }

    private fun DataInputCodeGenerationContext.shouldAlwaysGenerateUseStatement(): Boolean {
        return when(this) {
            DataInputCodeGenerationContext.ON_DROP -> false
            DataInputCodeGenerationContext.FOR_EXECUTION -> true
            DataInputCodeGenerationContext.AFTER_LOADING -> false
            DataInputCodeGenerationContext.BEFORE_SAVE -> true
            DataInputCodeGenerationContext.FOR_CONVERSION -> true
        }
    }

    private fun isDataFrameInClasspath(notebookFile: BackedNotebookVirtualFile, project: Project): Boolean {
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return compilerService.currentClasspath.any { file ->
            isTargetLibrary(file)
        }
    }

    private fun generateCode(
        importExpression: String,
        dfName: String,
        shouldGenerateUseStatement: Boolean,
    ): String =
        listOfNotNull(
            getUseStatement().takeIf { shouldGenerateUseStatement },
            """
            val $dfName = $importExpression
            ${getCellResultStatement(dfName)}
            """.trimIndent()
        ).joinToString("")
}
