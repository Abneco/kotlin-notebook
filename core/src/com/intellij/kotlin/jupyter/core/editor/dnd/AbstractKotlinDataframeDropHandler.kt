// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.editor.handlers.DataframeVariableNameSuggester
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerParams
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
        params: TableDataFileDropHandlerParams
    ): String

    override fun generateCellCode(params: TableDataFileDropHandlerParams): String {
        val dataFilePath = createFilePath(params.tableDataFile, params.notebookFile.file, params.project)
        val dfName = params.dataframeName ?: nameSuggester.createDataframeName(params.project, params.tableDataFile)
        val importExpression = generateImportExpression(dataFilePath, params)
        return generateCode(importExpression, dfName, params.notebookFile, params.project, params.fileIndex)
    }
}
