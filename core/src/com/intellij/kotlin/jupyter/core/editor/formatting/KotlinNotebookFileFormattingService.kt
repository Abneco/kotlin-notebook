// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.formatting

import com.intellij.formatting.FormatTextRanges
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.injected.editor.DocumentWindow
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.buildFlatMap
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.restartAnalyzing
import com.intellij.kotlin.jupyter.core.util.toDocument
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

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

        val cellList = jupyterPsiFile.getNotebookCells().ifEmpty { return }
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val filesToProcess = cellList
            .buildFlatMap { addKotlinFilesWithRanges(it, formattingRanges, injectedManager) }
            .ifEmpty { return }

        try {
            val formatter = EP_NAME.findExtensionOrFail(CoreFormattingService::class.java)
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
            notebookLogger().warn("Error occurred during reformatting of Kotlin Notebook", e)
        } finally {
            ReadAction.run<Throwable> {
                jupyterPsiFile.restartAnalyzing(this)
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
