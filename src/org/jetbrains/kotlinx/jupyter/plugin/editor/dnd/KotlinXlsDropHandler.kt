// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import com.intellij.jupyter.core.editor.handlers.TableDataFileExtensions
import java.io.File

class KotlinXlsDropHandler : AbstractKotlinDataframeDropHandler(
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.xls.dataframe.command"),
    setOf(
        TableDataFileExtensions.XLS,
        TableDataFileExtensions.XLSX,
        TableDataFileExtensions.XLSM
    )
) {
    override fun generateImportExpression(importedFile: File, dataFilePath: String): String {
        return "DataFrame.readExcel(\"$dataFilePath\")"
    }
}
