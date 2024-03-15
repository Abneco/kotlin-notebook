// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.formatting

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
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.retrieveCellIntervalUnderCaret
import org.jetbrains.kotlinx.jupyter.plugin.util.buildFlatMap
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.util.toDocument
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
        if (!jupyterPsiFile.isValid) return
        val project = formattingContext.project

        // TODO: This logic should be in a listener
        val highlightingDataProvider = jupyterPsiFile.virtualFile.toBackedNotebookFile()?.let {
            NotebookHighlightingService.getForFile(project, it).dataController
        }
        val renamingRanges = highlightingDataProvider?.renamingRanges
        if (!renamingRanges.isNullOrEmpty()) return

        val cellList = jupyterPsiFile.getNotebookCells().ifEmpty { return }
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val filesToProcess = cellList
            .buildFlatMap { addKotlinFilesWithRanges(it, formattingRanges, injectedManager) }
            .ifEmpty { return }

        // TODO: This logic should be in a listener
        val invokedInCell = document.retrieveCellIntervalUnderCaret(jupyterPsiFile.virtualFile, project)
        highlightingDataProvider?.update {
            reformatDocumentTargets = mutableSetOf()
        }

        // TODO: Possibly could be removed
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
            // TODO: This logic should be in a listener
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

    private fun MutableList<FileWithRanges>.addKotlinFilesWithRanges(
        cell: JupyterPsiCell,
        formattingRanges: MutableList<TextRange>,
        injectedManager: InjectedLanguageManager
    ) {
        val cellRange = cell.textRange
        val hostRanges: List<TextRange> = formattingRanges
            .filter { range -> range.intersectsStrict(cellRange) }
            .ifEmpty { return }
        val ktFiles = cell.getInjectedKtFiles(injectedManager).ifEmpty { return }

        for (ktFile in ktFiles) {
            val documentWindow = ktFile.toDocument() as? DocumentWindow ?: continue

            val ranges = hostRanges.mapNotNull { range ->
                ProperTextRange(
                    documentWindow.hostToInjected(range.start),
                    documentWindow.hostToInjected(range.end)
                ).takeIf { !it.isEmpty }
            }.takeIf { it.isNotEmpty() } ?: continue

            add(FileWithRanges(ktFile, ranges))
        }
    }

    private data class FileWithRanges(val file: KtFile, val ranges: List<TextRange>)
}
