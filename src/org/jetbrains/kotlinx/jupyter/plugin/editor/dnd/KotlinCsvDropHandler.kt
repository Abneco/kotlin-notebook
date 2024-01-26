// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.plugins.notebooks.core.impl.file.notebookOrNull
import org.jetbrains.plugins.notebooks.editor.handlers.LanguageTableDataFileDropHandler
import org.jetbrains.plugins.notebooks.editor.handlers.TableDataFileExtensions
import org.jetbrains.plugins.notebooks.editor.handlers.createCsvPath
import org.jetbrains.plugins.notebooks.editor.handlers.guessCsvSeparator
import java.io.File

class KotlinCsvDropHandler : LanguageTableDataFileDropHandler(
    KotlinLanguage.INSTANCE,
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.csv.dataframe.command"),
    setOf(TableDataFileExtensions.EXTENSION_CSV)
) {
    override fun generateCellsCode(editor: Editor, csvFile: File, fileIndex: Int): List<String> {
        val csvSeparator = guessCsvSeparator(csvFile)
        val separatorArg = csvSeparator?.let { separator ->
            val escapedSeparator = when (separator) {
                '\t' -> "\\t"
                else -> separator
            }
            ", delimiter = '$escapedSeparator'"
        } ?: ""

        val csvPath = createCsvPath(csvFile, editor)
        return listOfNotNull(
            "%use dataframe\n".takeIf { fileIndex == 0 && !isDataFrameInClasspath(editor) },
            """
                val df = DataFrame.readCSV("$csvPath"$separatorArg)
                df
            """.trimIndent()
        )
    }

    private fun isDataFrameInClasspath(editor: Editor): Boolean {
        val notebookFile = editor.notebookOrNull ?: return false
        val project = editor.project ?: return false
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return compilerService.currentClasspath.any { file ->
            file.name.startsWith("dataframe-core")
        }
    }
}
