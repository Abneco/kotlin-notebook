// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.components

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.utils.addIfNotNull


/**
 * Handles highlighting pass token processing inside [com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingManager].
 *
 * This modular class consists of main components:
 *  - error highlighters
 *  - processing highlighted tokens inside injections
 *
 * This class is responsible for synchronizing state relevant to components and
 * determine if particular highlighting pass completed, e.g., tokens are applied
 * to [com.intellij.openapi.editor.markup.MarkupModel].
 *
 */
internal class HighlightingPassTokensProcessor(
    project: Project,
    parentDisposable: Disposable
) : Disposable {
    companion object {
        private val LOG = thisLogger()
    }

    init {
        Disposer.register(parentDisposable, this)
    }

    private val errorHighlightersProcessor = ErrorHighlightersProcessor(this, LOG)
    private val injectedFilesDataProcessor = InjectedFilesDataProcessor(
        InjectedLanguageManager.getInstance(project),
        LOG,
        this
    )

    val remainingIndexesToProcess: Set<Int>
        get() = injectedFilesDataProcessor.targetIndexes - finishedFiles

    val finishedFiles: Set<Int>
        get() = injectedFilesDataProcessor.finishedFilesIndexes - errorHighlightersProcessor.fileIndexesToErrors.keys

    fun passCreated(targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        injectedFilesDataProcessor.passCreated(targetIndexes, cells, completeRangeInd)
    }

    fun getInjectionHost(psiFile: PsiFile): PsiLanguageInjectionHost? {
        return injectedFilesDataProcessor.getFileInjectionData(psiFile)?.injectionHost
    }

    fun isFileTarget(psiFile: PsiFile): Boolean {
        return injectedFilesDataProcessor.isFileTarget(psiFile)
    }

    fun injectedFileProcessed(psiFile: PsiFile) {
        injectedFilesDataProcessor.finishedForFile(psiFile)
    }

    fun getErrorHighlighters(psiFile: PsiFile): Set<RangeHighlighter> {
        val errorHighlighters = errorHighlightersProcessor.fileIndexesToErrors
        val cellInd = injectedFilesDataProcessor.getFileInjectionData(psiFile)?.notebookCellIndex ?: return emptySet()

        if (isFileTarget(psiFile)) {
            errorHighlighters.putIfAbsent(cellInd, mutableSetOf())
        }

        return errorHighlighters[cellInd] ?: emptySet()
    }

    fun determineCellIndexesLeftToHighlight(
        queue: MutableSet<Int>?,
        topLevelFile: PsiFile?,
        markupModel: MarkupModelEx,
        cellFocusIndex: Int?
    ): MutableSet<Int> {
        injectedFilesDataProcessor.finishedFilesIndexes.addIfNotNull(cellFocusIndex)
        val finishedFilesIndexes = injectedFilesDataProcessor.finishedFilesIndexes
        val remaining = remainingIndexesToProcess.toMutableSet()
        remaining.remove(cellFocusIndex)

        errorHighlightersProcessor.determineFilesWithLeftErrors(markupModel, finishedFilesIndexes, cellFocusIndex)

        injectedFilesDataProcessor.determineFilesLeftToHighlight(markupModel, remaining)
        injectedFilesDataProcessor.processUnrecognizedFiles(topLevelFile, queue)

        return remaining
    }


    fun editorCreated(editor: Editor) {
        errorHighlightersProcessor.addMarkupListener(editor)
    }

    /**
     * Reset processor state before new HL pass.
     *
     * If no kernel restart performed, the information from [errorHighlightersProcessor] should be kept
     * between iterations as some errors might not be HL-ed/disposed of if analysis was interrupted.
     */
    fun clearState(cellFocusIndex: Int?, onRestart: Boolean = false) {
        injectedFilesDataProcessor.clear()

        if (onRestart) {
            errorHighlightersProcessor.clear()
        } else {
            // transfer seen errors to a proper storage
            val highlighters = errorHighlightersProcessor.targetErrorHighlighters
            if (cellFocusIndex != null) {
                val focusCellHighlighters = errorHighlightersProcessor.fileIndexesToErrors.getOrPut(cellFocusIndex) { mutableSetOf() }
                focusCellHighlighters?.addAll(highlighters)
            }
            highlighters.clear()
        }
    }

    override fun dispose() {
        clearState(null, true)
    }
}
