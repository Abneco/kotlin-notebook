// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions.refactor

import com.intellij.configurationStore.runAsWriteActionIfNeeded
import com.intellij.formatting.FormatTextRanges
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.idea.editor.fixers.end
import org.jetbrains.kotlin.idea.editor.fixers.start
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.file.getInjectedKtFile
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.retrieveCellIntervalUnderCaret
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.toBackedNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

class KotlinNotebookFileFormattingService : AbstractDocumentFormattingService() {
    override fun getFeatures(): Set<FormattingService.Feature> =
        setOf(FormattingService.Feature.AD_HOC_FORMATTING, FormattingService.Feature.FORMAT_FRAGMENTS)

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
        val jupyterPsiFile = formattingContext.containingFile
        val project = formattingContext.project
        val injectedManager = InjectedLanguageManager.getInstance(project)
        if (!jupyterPsiFile.isValid) return
        val highlightingDataProvider = jupyterPsiFile.virtualFile.toBackedNotebookFile()?.let {
            NotebookHighlightingService.getForFile(project, it).dataController
        }
        if (highlightingDataProvider?.renamingRanges != null) return

        val cellList = jupyterPsiFile.getNotebookCellList() ?: return

        val filesToProcess = cellList
            .mapNotNull { getKotlinFileWithRanges(it, formattingRanges, injectedManager) }
            .takeIf { it.isNotEmpty() } ?: return

        val invokedInCell = document.retrieveCellIntervalUnderCaret(jupyterPsiFile.virtualFile, project)

        highlightingDataProvider?.update {
            reformatDocumentTargets = mutableSetOf()
        }

        if (filesToProcess.any { !it.file.isValid }) {
            runAsWriteActionIfNeeded {
                jupyterPsiFile.viewProvider.contentsSynchronized()
            }
        }

        try {
            val formatter = FormattingService.EP_NAME.findExtensionOrFail(CoreFormattingService::class.java)
            for ((file, ranges) in filesToProcess) {
                val rangeInfo = FormatTextRanges().apply {
                    ranges.forEach { add(it, false) }
                    isExtendToContext = true
                }
                formatter.formatRanges(file, rangeInfo, canChangeWhiteSpaceOnly, quickFormat)
            }
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                throw e
            }
            thisLogger().warn("Error occurred during reformatting of Kotlin Notebook", e)
        } finally {
            val targets = highlightingDataProvider?.reformatDocumentTargets
            highlightingDataProvider?.update {
                reformatDocumentTargets = null
                invokedInCell?.ordinal?.let {
                    notebookChangedCellIndex = it
                }
                notebookDocumentTargetRanges = targets
            }

            if (!DaemonCodeAnalyzerStatusService.getInstance(project).daemonRunning) {
                invokeLater {
                    jupyterPsiFile.restartAnalyzing()
                }
            }
        }
    }

    private fun getKotlinFileWithRanges(
        cell: JupyterPsiCell,
        formattingRanges: MutableList<TextRange>,
        injectedManager: InjectedLanguageManager
    ): FileWithRanges? {
        val cellRange = cell.textRange
        val hostRanges: List<TextRange> = formattingRanges
            .filter { range -> range.intersectsStrict(cellRange) }
        if (hostRanges.isEmpty()) return null
        val ktFile = cell.getInjectedKtFile(injectedManager) ?: return null
        val documentWindow = ktFile.toDocument(cell.project) as? DocumentWindow ?: return null

        val ranges = hostRanges.mapNotNull { range ->
            ProperTextRange(documentWindow.hostToInjected(range.start), documentWindow.hostToInjected(range.end)).takeIf { !it.isEmpty }
        }.takeIf { it.isNotEmpty() } ?: return null

        return FileWithRanges(ktFile, ranges)
    }

    private data class FileWithRanges(val file: KtFile, val ranges: List<TextRange>)
}
