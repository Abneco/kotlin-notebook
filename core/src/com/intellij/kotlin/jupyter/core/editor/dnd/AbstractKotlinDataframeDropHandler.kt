// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.handlers.DataframeVariableNameSuggester
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.createFilePath
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File

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
        importedFile: File,
        dataFilePath: String,
    ): String

    override fun generateCellsCode(
        notebookFile: BackedNotebookVirtualFile,
        project: Project,
        tableDataFile: File,
        fileIndex: Int,
        dataframeName: String?
    ): List<String> {
        val dataFilePath = createFilePath(tableDataFile, notebookFile.file, project)
        val dfName = dataframeName ?: nameSuggester.createDataframeName(project, tableDataFile)
        val importExpression = generateImportExpression(tableDataFile, dataFilePath)
        return generateCode(importExpression, dfName, notebookFile, project, fileIndex)
    }
}
