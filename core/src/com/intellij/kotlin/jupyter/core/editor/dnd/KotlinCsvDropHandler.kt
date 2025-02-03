// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.editor.handlers.TableDataFileExtensions
import com.intellij.jupyter.core.editor.handlers.guessCsvSeparator
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import java.io.File

class KotlinCsvDropHandler : AbstractKotlinDataframeDropHandler(
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.csv.dataframe.command"),
    setOf(TableDataFileExtensions.CSV, TableDataFileExtensions.TSV)
) {
    override fun generateImportExpression(importedFile: File, dataFilePath: String, isFastMode: Boolean): String {
        val csvSeparator = if (isFastMode) null else guessCsvSeparator(importedFile)
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
