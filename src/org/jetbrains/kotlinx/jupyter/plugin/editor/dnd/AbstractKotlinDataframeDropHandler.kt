// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import com.intellij.jupyter.core.editor.handlers.LanguageTableDataFileDropHandler
import com.intellij.jupyter.core.editor.handlers.createDataframeName
import com.intellij.jupyter.core.editor.handlers.createFilePath
import java.io.File

abstract class AbstractKotlinDataframeDropHandler(
    @Nls commandName: String,
    fileExtensions: Set<String>
): LanguageTableDataFileDropHandler(
    KotlinLanguage.INSTANCE,
    commandName,
    fileExtensions
) {
    protected abstract fun generateImportExpression(
        importedFile: File,
        dataFilePath: String,
    ): String

    override fun generateCellsCode(editor: Editor, tableDataFile: File, fileIndex: Int): List<String> {
        val dataFilePath = createFilePath(tableDataFile, editor)
        val dfName =
            createDataframeName(editor.project, tableDataFile, KotlinDataframeVariableNameSuggester)
        val importExpression = generateImportExpression(tableDataFile, dataFilePath)
        return generateCode(importExpression, dfName, editor, fileIndex)
    }
}
