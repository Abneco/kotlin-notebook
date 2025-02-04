// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.editor.handlers.DataframeVariableNameSuggester
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.jupyter.core.editor.handlers.createFilePath
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
        val dataFilePath = createFilePath(context.tableDataFile, context.notebookFile.file, context.project)
        val dfName = context.dataframeName ?: nameSuggester.createDataframeName(context.project, context.tableDataFile.nameWithoutExtension)
        val importExpression = generateImportExpression(dataFilePath, context)
        return generateCode(importExpression, dfName, context.notebookFile, context.project, context.fileIndex)
    }
}
