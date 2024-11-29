// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.addDisposableChild
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
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
internal class HighlightingPassStateTracker(
    project: Project,
) : Disposable {
    private val myErrorHighlightersTracker = addDisposableChild(
        ErrorHighlightersTracker()
    )
    private val myInjectedFilesDataTracker = addDisposableChild(
        InjectedFilesDataTracker()
    )

    val remainingIndexesToProcess: Set<Int>
        get() = myInjectedFilesDataTracker.targetIndexes - finishedFiles

    val finishedFiles: Set<Int>
        get() = myInjectedFilesDataTracker.finishedFilesIndexes - myErrorHighlightersTracker.fileIndexesToErrors.keys

    fun passCreated(targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        // redirection is not appreciated
        myInjectedFilesDataTracker.passCreated(targetIndexes, cells, completeRangeInd)
    }

    fun getInjectionHost(psiFile: PsiFile): PsiLanguageInjectionHost? {
        return myInjectedFilesDataTracker.getFileInjectionData(psiFile)?.injectionHost
    }

    /**
     * Disposes any stored error highlighters outside current [cellInFocus] index
     */
    fun disposeErrorHighlighters(cellInFocus: Int?) {
        val errorData = myErrorHighlightersTracker.fileIndexesToErrors.filter { entry ->
            entry.key in myInjectedFilesDataTracker.targetIndexes
                    && entry.value.isNotEmpty()
                    && entry.key != cellInFocus
        }

        KotlinNotebookPluginScope.invokeOnEDT {
            errorData.forEach { entry ->
                entry.value.forEach { highlighter ->
                    highlighter.dispose()
                }
            }
        }
    }

    fun isFileTarget(psiFile: PsiFile): Boolean {
        return myInjectedFilesDataTracker.isFileTarget(psiFile)
    }

    fun injectedFileProcessed(psiFile: PsiFile) {
        myInjectedFilesDataTracker.finishedForFile(psiFile)
    }

    fun getErrorHighlighters(psiFile: PsiFile): Set<RangeHighlighter> {
        val errorHighlighters = myErrorHighlightersTracker.fileIndexesToErrors
        val cellInd = myInjectedFilesDataTracker.getFileInjectionData(psiFile)?.notebookCellIndex ?: return emptySet()

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
        myInjectedFilesDataTracker.finishedFilesIndexes.addIfNotNull(cellFocusIndex)
        val finishedFilesIndexes = myInjectedFilesDataTracker.finishedFilesIndexes
        val remaining = remainingIndexesToProcess.toMutableSet()
        remaining.remove(cellFocusIndex)

        myErrorHighlightersTracker.determineFilesWithRemainingErrors(markupModel, finishedFilesIndexes, cellFocusIndex)

        myInjectedFilesDataTracker.determineFilesLeftToHighlight(markupModel, remaining)
        myInjectedFilesDataTracker.processUnrecognizedFiles(topLevelFile, queue)

        return remaining
    }


    fun editorCreated(editor: Editor) {
        myErrorHighlightersTracker.addMarkupListener(editor)
    }

    /**
     * Reset processor state before new HL pass.
     *
     * If no kernel restart performed, the information from [myErrorHighlightersTracker] should be kept
     * between iterations as some errors might not be HL-ed/disposed of if analysis was interrupted.
     */
    fun clearState(cellFocusIndex: Int?, onRestart: Boolean = false) {
        myInjectedFilesDataTracker.clear()

        if (onRestart) {
            myErrorHighlightersTracker.clear()
        } else {
            // transfer seen errors to a proper storage
            val highlighters = myErrorHighlightersTracker.targetErrorHighlighters
            if (cellFocusIndex != null) {
                val focusCellHighlighters = myErrorHighlightersTracker.fileIndexesToErrors.getOrPut(cellFocusIndex) { mutableSetOf() }
                focusCellHighlighters?.addAll(highlighters)
            }
            highlighters.clear()
        }
    }

    override fun dispose() {
        clearState(null, true)
    }
}
