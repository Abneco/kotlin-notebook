// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.NotebookPassConfiguration
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.numberOfNonWhiteSpaceLeaves
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterLayer.SYNTAX
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile

internal class NotebookPassProgressTracker : PassProgressTracker, HighlightingComponent() {
    companion object {
        internal const val INJECTED_SYNTAX_LAYER_BORDER = HighlighterLayer.CARET_ROW - 1
    }
    private val injectedFilesDataRegistry = ConcurrentCollectionFactory.createConcurrentMap<KtFile, InjectedFileData>()

    /**
     * Accumulator of pass information
     */
    private lateinit var currentConfiguration: NotebookPassConfiguration
    override val passConfiguration: NotebookPassConfiguration
        get() = ::currentConfiguration.isInitialized.let {
            if (it) currentConfiguration else NotebookPassConfiguration.EMPTY
        }

    override fun passStarting(
        file: PsiFile,
        focusCellIndex: Int,
        indexesToHighlight: Collection<Int>,
        cellsToHighlight: List<PsiLanguageInjectionHost>,
    ) {
        injectedFilesDataRegistry.clear()

        val newConfiguration = createPassNewConfiguration(
            focusCellIndex,
            cellsToHighlight,
            indexesToHighlight
        )

        currentConfiguration = newConfiguration
    }

    private fun createPassNewConfiguration(
        focusCellIndex: Int,
        cells: List<PsiLanguageInjectionHost>,
        targetIndexes: Collection<Int>
    ): NotebookPassConfiguration {
        // todo: assert?
        if (cells.isEmpty()) {
            return NotebookPassConfiguration(
                focusCellIndex,
                emptyMap(),
                cells
            )
        }

        val project = cells.first().project
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)

        for (ind in targetIndexes) {
            val psiCell = cells.getOrNull(ind) ?: continue
            val injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(psiCell)
            if (injectedPsiFiles == null) {
                continue
            }

            // skip non Kt
            if (injectedPsiFiles.none { f -> f.first is KtFile }) {
                continue
            }
            injectedPsiFiles.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                val ktFileRange = injectedLanguageManager.injectedToHost(ktFile, ktFile.textRange)
                injectedFilesDataRegistry[ktFile as KtFile] = InjectedFileData(
                    ind,
                    ktFile,
                    ktFileRange,
                    psiCell,
                    numberOfNonWhiteSpaceLeaves(ktFile)
                )
            }
        }

        return NotebookPassConfiguration(
            focusCellIndex,
            injectedFilesDataRegistry,
            cells
        )
    }

    override fun getStatusAfterPassFinished(editor: EditorEx): NotebookPassProgressStatus {
        val markupModelEx = editor.filteredDocumentMarkupModel

        val data = injectedFilesDataRegistry
        val skippedFiles = mutableSetOf<Int>()
        val errorsToDispose = mutableSetOf<RangeHighlighter>()

        for ((_, fileData) in data) {
            val range = fileData.ktFileRange
            if (range.length == 0) continue

            fileData.appliedTokens.set(0)
            val seenHighlighters = mutableSetOf<RangeHighlighter>()

            markupModelEx.processRangeHighlightersOverlappingWith(range.startOffset, range.endOffset) {
                // injected syntax is greater than regular SYNTAX
                val layer = it.layer
                if ((layer in SYNTAX..INJECTED_SYNTAX_LAYER_BORDER) && seenHighlighters.add(it)) {
                    fileData.appliedTokens.incrementAndGet()
                }
                if (layer == HighlighterLayer.ERROR && fileData.notebookCellIndex != passConfiguration.focusCell) {
                    errorsToDispose.add(it)
                }
                true
            }
            val tokens = seenHighlighters.size

            if (tokens < fileData.totalTokens - 1) {
                skippedFiles.add(fileData.notebookCellIndex)
            }
        }

        return NotebookPassProgressStatus(skippedFiles, errorsToDispose)
    }

    override fun dispose() {
        super.dispose()
        injectedFilesDataRegistry.clear()
    }
}