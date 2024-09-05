// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.editor.handlers.TableDataFileExtensions
import org.jetbrains.plugins.notebooks.editor.handlers.guessCsvSeparator
import java.io.File

class KotlinCsvDropHandler : AbstractKotlinDataframeDropHandler(
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.csv.dataframe.command"),
    setOf(TableDataFileExtensions.CSV, TableDataFileExtensions.TSV)
) {
    override fun generateImportExpression(importedFile: File, dataFilePath: String): String {
        val csvSeparator = guessCsvSeparator(importedFile)
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
