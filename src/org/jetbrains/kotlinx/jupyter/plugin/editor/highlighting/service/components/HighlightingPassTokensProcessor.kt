// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.components

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
 * Handles highlighting pass token processing inside [org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingManager].
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

    private val errorHighlighterComponent = ErrorHighlighterComponent(this, LOG)
    private val injectedFilesDataComponent = InjectedFilesDataComponent(
        InjectedLanguageManager.getInstance(project),
        LOG,
        this
    )

    val remainingIndexesToProcess: Set<Int>
        get() = injectedFilesDataComponent.targetIndexes - finishedFiles

    val finishedFiles: Set<Int>
        get() = injectedFilesDataComponent.finishedFiles - errorHighlighterComponent.knownErrorIndices.keys

    fun passCreated(targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        injectedFilesDataComponent.passCreated(targetIndexes, cells, completeRangeInd)
    }

    fun getInjectionHost(psiFile: PsiFile): PsiLanguageInjectionHost? {
        return injectedFilesDataComponent.getFileInjectionData(psiFile)?.injectionHost
    }

    fun isFileTarget(psiFile: PsiFile): Boolean {
        return injectedFilesDataComponent.isFileTarget(psiFile)
    }

    fun injectedFileProcessed(psiFile: PsiFile) {
        injectedFilesDataComponent.finishedForFile(psiFile)
    }

    fun getErrorHighlighters(psiFile: PsiFile): Set<RangeHighlighter> {
        val errorHighlighters = errorHighlighterComponent.knownErrorIndices
        val cellInd = injectedFilesDataComponent.getFileInjectionData(psiFile)?.notebookCellIndex ?: return emptySet()

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
        injectedFilesDataComponent.finishedFiles.addIfNotNull(cellFocusIndex)
        val finishedFiles = injectedFilesDataComponent.finishedFiles
        val remaining = remainingIndexesToProcess.toMutableSet()
        remaining.remove(cellFocusIndex)

        errorHighlighterComponent.determineFilesWithLeftErrors(markupModel, finishedFiles, cellFocusIndex)

        injectedFilesDataComponent.determineFilesLeftToHighlight(markupModel, remaining)
        injectedFilesDataComponent.processUnrecognizedFiles(topLevelFile, queue)

        return remaining
    }


    fun editorCreated(editor: Editor) {
        errorHighlighterComponent.addMarkupListener(editor)
    }

    fun clearState(cellFocusIndex: Int?, onRestart: Boolean = false) {
        injectedFilesDataComponent.clear(onRestart)

        if (onRestart) {
            errorHighlighterComponent.clear()
        } else {
            val highlighted = errorHighlighterComponent.targetErrorHighlighters
            if (cellFocusIndex != null) {
                errorHighlighterComponent.knownErrorIndices[cellFocusIndex]?.addAll(highlighted)
            }
            highlighted.clear()
        }

    }

    override fun dispose() {
        clearState(null, true)
    }
}
