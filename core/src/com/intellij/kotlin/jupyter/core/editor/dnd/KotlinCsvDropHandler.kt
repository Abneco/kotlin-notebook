// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.jupyter.core.editor.handlers.TableDataFileExtensions
import com.intellij.jupyter.core.editor.handlers.guessCsvSeparator
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle

class KotlinCsvDropHandler : AbstractKotlinDataframeDropHandler(
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.csv.dataframe.command"),
    setOf(TableDataFileExtensions.CSV, TableDataFileExtensions.TSV)
) {
    override fun generateImportExpression(dataFilePath: String, context: TableDataFileDropHandlerContext): String {
        val csvSeparator: Char? = when (val pathData = context.pathData) {
            is TableDataFileDropHandlerContext.PathData.FileBased -> guessCsvSeparator(pathData.tableDataFile)
            is TableDataFileDropHandlerContext.PathData.Lightweight -> null
        }
        val separatorArg = csvSeparator?.let { separator ->
            val escapedSeparator = when (separator) {
                '\t' -> "\\t"
                else -> separator
            }
            ", delimiter = '$escapedSeparator'"
        } ?: ""

        return "DataFrame.readCSV(\"$dataFilePath\"$separatorArg)"
    }
}
