// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.editor.handlers.LanguageTableDataFileDropHandler
import org.jetbrains.plugins.notebooks.editor.handlers.TableDataFileExtensions
import org.jetbrains.plugins.notebooks.editor.handlers.createDataframeName
import org.jetbrains.plugins.notebooks.editor.handlers.createFilePath
import org.jetbrains.plugins.notebooks.editor.handlers.guessCsvSeparator
import java.io.File

class KotlinCsvDropHandler : LanguageTableDataFileDropHandler(
    KotlinLanguage.INSTANCE,
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.csv.dataframe.command"),
    setOf(TableDataFileExtensions.CSV, TableDataFileExtensions.TSV)
) {
    override fun generateCellsCode(editor: Editor, tableDataFile: File, fileIndex: Int): List<String> {
        val csvSeparator = guessCsvSeparator(tableDataFile)
        val separatorArg = csvSeparator?.let { separator ->
            val escapedSeparator = when (separator) {
                '\t' -> "\\t"
                else -> separator
            }
            ", delimiter = '$escapedSeparator'"
        } ?: ""

        val csvPath = createFilePath(tableDataFile, editor)
        val dfName =
            createDataframeName(editor.project, tableDataFile, KotlinDataframeVariableNameSuggester)
        return generateCode("DataFrame.readCSV(\"$csvPath\"$separatorArg)", dfName, editor, fileIndex)
    }
}
