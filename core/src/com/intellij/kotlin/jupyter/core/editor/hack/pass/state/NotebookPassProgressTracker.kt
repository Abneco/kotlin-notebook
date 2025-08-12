// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass.state

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.hack.NotebookPassConfiguration
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.InjectedFilesDataTracker.Companion.INJECTED_SYNTAX_LAYER_BORDER
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.numberOfNonWhiteSpaceLeaves
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterLayer.SYNTAX
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile

internal class NotebookPassProgressTracker : PassProgressTracker, HighlightingComponent() {
    internal val leftIndexes: Collection<Int>
        get() = passConfiguration.filesToHL.values.map { it.notebookCellIndex } - passConfiguration.completedFiles
    private val injectedFilesDataRegistry = ConcurrentCollectionFactory.createConcurrentMap<KtFile, InjectedFileData>()
    private val finishedFilesIndexes: MutableSet<Int> = ConcurrentCollectionFactory.createConcurrentSet()

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

    // Might better as Int, not KtFile
    override val leftToHighlight: Collection<KtFile>
        get() {
            val leftIndexes = leftIndexes.toSet()

            return currentConfiguration.filesToHL.filter { (_, data) ->
                data.notebookCellIndex !in leftIndexes
            }.map { (file, _) -> file }
        }

    private fun createPassNewConfiguration(
        focusCellIndex: Int,
        cells: List<PsiLanguageInjectionHost>,
        targetIndexes: Collection<Int>
    ): NotebookPassConfiguration {
        finishedFilesIndexes.clear()
        // todo: assert?
        if (cells.isEmpty()) {
            return NotebookPassConfiguration(
                focusCellIndex,
                emptyMap(),
                null,
                ConcurrentCollectionFactory.createConcurrentSet()
            )
        }

        val project = cells.first().project
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        var targetPsiFile: KtFile? = null

        for (ind in targetIndexes) {
            val psiCell = cells.getOrNull(ind) ?: continue
            val injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(psiCell)
            if (injectedPsiFiles == null) {
                continue
            }

            // skip non Kt
            if (injectedPsiFiles.none { f -> f.first is KtFile }) {
                finishedFilesIndexes.add(ind)
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

                if (ind == focusCellIndex) {
                    targetPsiFile = ktFile
                }
            }
        }

        return NotebookPassConfiguration(
            focusCellIndex,
            injectedFilesDataRegistry,
            targetPsiFile,
            finishedFilesIndexes
        )
    }

    override fun getRemainingProgressAfterPassFinished(editor: EditorEx): NotebookPassProgressStatus {
        val markupModelEx = editor.filteredDocumentMarkupModel

        val data = injectedFilesDataRegistry
        val skippedFiles = mutableSetOf<Int>()
        val errorsToDispose = mutableSetOf<RangeHighlighter>()

        for ((_, fileData) in data) {
            val range = fileData.ktFileRange
            if (range.length == 0) continue

            fileData.processedTokens.set(0)
            val seenHighlighters = mutableSetOf<RangeHighlighter>()

            markupModelEx.processRangeHighlightersOverlappingWith(range.startOffset, range.endOffset) {
                // injected syntax is greater than regular SYNTAX
                val layer = it.layer
                if ((layer in SYNTAX..INJECTED_SYNTAX_LAYER_BORDER) && seenHighlighters.add(it)) {
                    fileData.processedTokens.incrementAndGet()
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