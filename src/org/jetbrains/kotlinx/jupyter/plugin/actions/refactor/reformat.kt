// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions.refactor

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.configurationStore.runAsWriteActionIfNeeded
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.plugin.file.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.CompleteHighlightingRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.ReformatDocumentActionTargets
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.retrieveCellIntervalUnderCaret
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
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
        if (!asPsiFile.isValid || formattingRanges.firstOrNull()?.length == 1) {
            runAsWriteActionIfNeeded {
                asPsiFile.viewProvider.contentsSynchronized()
            }
            return
        }

        val cellList = asPsiFile.getNotebookCellList() ?: return
        val project = formattingContext.project
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val formattingRangesSet = formattingRanges.toSet()
        val documentLength = document.textLength
        val isWholeDocumentReformat = !quickFormat && isReformationWholeDocument(formattingRanges, documentLength)

        val toProcess = if (isWholeDocumentReformat)
                            cellList.getInjectedKtFiles(injectedManager)
                        else cellList.filter { it.textRange in formattingRangesSet }.getInjectedKtFiles(injectedManager)
        if (toProcess.isEmpty()) {
            //asPsiFile.viewProvider.contentsSynchronized()
            return
        }

        val invokedInCell = document.retrieveCellIntervalUnderCaret(asPsiFile.virtualFile, project)
        val afterUpdate = {
            val targets = synchronized(document) {
                document.getUserData(ReformatDocumentActionTargets)
            }
            document.putUserData(ReformatDocumentActionTargets, null)
            if (invokedInCell != null) {
                document.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
            }
            document.putUserData(NotebookDocumentTargetRanges, targets)
            invokedInCell?.ordinal?.let {
                document.putUserData(CompleteHighlightingRange, cellList.get(it)?.textRange)
            }

            invokeLater {
                asPsiFile.restartAnalyzing()
            }
        }
        document.putUserData(ReformatDocumentActionTargets, mutableSetOf())

        if (!isWholeDocumentReformat && toProcess.any { !it.isValid }) {
            runAsWriteActionIfNeeded {
                asPsiFile.viewProvider.contentsSynchronized()
            }
        }
        // FORMATTER_TAGS_ENABLED
        val baseProcessor = ReformatCodeProcessor(project,
                                                  toProcess.toTypedArray(), afterUpdate,
                                                  !isWholeDocumentReformat)
        try {
            if (isWholeDocumentReformat) baseProcessor.run()
            else SideEffectGuard.computeWithoutSideEffects<Unit, Exception> { baseProcessor.run() }
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                throw e
            }
            thisLogger().debug("Error occurred during reformatting ${e.message}")
        }
    }

    private fun isReformationWholeDocument(ranges: Collection<TextRange>, documentLength: Int) =
        ranges.size == 1 && ranges.first().length == documentLength

}