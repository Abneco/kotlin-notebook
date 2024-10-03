// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.components

import com.intellij.concurrency.ConcurrentCollectionFactory
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
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport


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

    private val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()
    val errorHighlighterComponent = ErrorHighlighterComponent(this, LOG)
    val injectedFilesDataComponent = InjectedFilesDataComponent(
        InjectedLanguageManager.getInstance(project),
        LOG,
        this
    )

    // make it mutable by default?
    val remainingIndexesToProcess: Set<Int>
        get() = injectedFilesDataComponent.targetIndexes - injectedFilesDataComponent.finishedFiles - errorHighlighterComponent.knownErrorIndices.keys

    fun passCreated(project: Project, targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        injectedFilesDataComponent.passCreated(project, targetIndexes, cells, completeRangeInd)

    }

    fun determineHighlightedFilesLeftToHighlight(
        queue: MutableSet<Int>?,
        topLevelFile: PsiFile,
        markupModel: MarkupModelEx,
        cellFocusIndex: Int?
    ): MutableSet<Int> {
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

    fun clear() {
        targetErrorHighlighters.clear()
        errorHighlighterComponent.clear()
        injectedFilesDataComponent.clear()
    }

    override fun dispose() {
        clear()
    }
}


internal fun isCanModifyHLRequests(project: Project): Boolean =
    !JupyterKtScriptingSupport.isInTheTransaction(project)