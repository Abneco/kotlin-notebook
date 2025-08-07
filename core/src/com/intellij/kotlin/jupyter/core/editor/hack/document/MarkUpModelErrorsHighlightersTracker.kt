// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.document

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.hack.NotebookPassConfiguration
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.markup.ShadowingAwareMarkupModelListener
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.util.concurrent.ConcurrentHashMap

/**
 * This class is responsible for tracking error highlighters
 * which should be disposed of after cell focus changes to preserve special notebook highlighting.
 *
 * @see [com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.KotlinNotebookShadowingVisitor]
 */
internal class MarkUpModelErrorsHighlightersTracker : HighlightingComponent() {
    private lateinit var activeMarkupModelListener: ShadowingAwareMarkupModelListener
    /**
     * Associates injected file indexes with their respective sets of error highlighters.
     * This information is used to dispose of old highlighters during Shadowing
     */
    val fileIndexesToErrors = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    private val _errorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()

    fun addMarkupListener(editor: Editor) {
        activeMarkupModelListener = ShadowingAwareMarkupModelListener(_errorHighlighters)

        val editorEx = editor as? EditorEx ?: return
        // todo: can really editor does not have a disposable? throw error then?
        val parentDisposable = (editor as? EditorImpl)?.disposable ?: this
        editorEx
            .filteredDocumentMarkupModel
            .addMarkupModelListener(parentDisposable, activeMarkupModelListener)
    }

    fun determineFilesWithRemainingErrors(
        markupModel: MarkupModelEx,
        passConfiguration: NotebookPassConfiguration
    ): Set<Int> {
        val focusCell = passConfiguration.focusCell

        val keys = fileIndexesToErrors.filterKeys { it != focusCell }
        val toRemove = mutableSetOf<Int>()
        keys.forEach { entry ->
            val data = fileIndexesToErrors[entry.key]
            data?.removeIf {
                it.layer == -1 || !it.isValid || !markupModel.containsHighlighter(it)
            }
            if (data?.isEmpty() == true) toRemove.add(entry.key)
        }

        fileIndexesToErrors[focusCell]?.addAll(_errorHighlighters)
        toRemove.forEach { fileIndexesToErrors.remove(it) }
        val targetPassed = focusCell in passConfiguration.completedFiles

        // todo: can it be checked without finishedFilesIndexes?
        return fileIndexesToErrors.filter {
            if (it.key != focusCell) it.value.isNotEmpty() else !targetPassed
        }.keys
    }

    fun clear() {
        fileIndexesToErrors.clear()
        _errorHighlighters.clear()
    }

    internal fun resetState(cellInFocus: Int?, completeReset: Boolean) {
        if (completeReset) {
            clear()
            return
        }

        // transfer seen errors to a dedicated storage
        val highlighters = _errorHighlighters
        if (cellInFocus != null) {
            val focusCellHighlighters = fileIndexesToErrors.getOrPut(cellInFocus) { mutableSetOf() }
            focusCellHighlighters?.addAll(highlighters)
        }
        highlighters.clear()
    }


    internal fun removeHighlightersOutSideOfFocus(passConfiguration: NotebookPassConfiguration) {
        val targetIndexes = passConfiguration.filesToHL.map { it.value.notebookCellIndex }
        val focusCell = passConfiguration.focusCell

        val errorData = fileIndexesToErrors.filter { entry ->
            entry.key in targetIndexes
                    && entry.value.isNotEmpty()
                    && entry.key != focusCell
        }

        KotlinNotebookPluginScope.invokeOnEDT {
            errorData.forEach { entry ->
                entry.value.forEach { highlighter ->
                    highlighter.dispose()
                }
            }
        }
    }

    override fun dispose() {
        clear()
        super.dispose()
    }
}