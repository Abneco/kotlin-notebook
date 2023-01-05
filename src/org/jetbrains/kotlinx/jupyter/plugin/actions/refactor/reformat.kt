// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions.refactor

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.plugin.file.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile

class KotlinNotebookFileFormattingService: AbstractDocumentFormattingService() {
    override fun getFeatures(): Set<FormattingService.Feature>
        = setOf(FormattingService.Feature.AD_HOC_FORMATTING, FormattingService.Feature.FORMAT_FRAGMENTS)

    override fun canFormat(file: PsiFile): Boolean {
        return file is JupyterFile && file.virtualFile.isKotlinNotebook
    }

    override fun formatDocument(
        document: Document,
        formattingRanges: MutableList<TextRange>,
        formattingContext: FormattingContext,
        canChangeWhiteSpaceOnly: Boolean,
        quickFormat: Boolean
    ) {
        val asPsiFile = formattingContext.containingFile
        val cellList = asPsiFile.getNotebookCellList() ?: return
        val project = formattingContext.project
        val injectedManager = InjectedLanguageManager.getInstance(project)

        val toProcess = cellList.getInjectedKtFiles(injectedManager)
        val afterUpdate = {
            document.putUserData(NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
        }
        val baseProcessor = ReformatCodeProcessor(project, toProcess.toTypedArray(), afterUpdate,  false)
        baseProcessor.run()
    }
}