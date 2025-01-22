// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.markup.ShadowingAwareMarkupModelListener
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.util.concurrent.ConcurrentHashMap


/**
 * Manages error highlighting within editors, including adding markup listeners and tracking error indices.
 *
 * This class's main goal is to keep track of error highlighters inside files during Shadowing.
 * On cell focus change, old error highlighters should be disposed of as a part of considering
 * this class being highlighted.
 */
internal class ErrorHighlightersTracker : Disposable {
    /**
     * Associates injected file indexes with their respective sets of error highlighters.
     * This information is used to dispose of old highlighters during Shadowing
     */
    val fileIndexesToErrors = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()

    private lateinit var activeMarkupModelListener: MarkupModelListener

    fun addMarkupListener(editor: Editor) {
        activeMarkupModelListener = ShadowingAwareMarkupModelListener(targetErrorHighlighters)

        val editorEx = editor as? EditorEx ?: return
        // todo: can really editor does not have a disposable? throw error then?
        val parentDisposable = (editor as? EditorImpl)?.disposable ?: this
        editorEx
            .filteredDocumentMarkupModel
            .addMarkupModelListener(parentDisposable, activeMarkupModelListener)
    }

    /**
     * This method should be called on daemonFinished event
     */
    fun determineFilesWithRemainingErrors(
        markupModel: MarkupModelEx,
        finishedFilesIndexes: MutableSet<Int>,
        completeIndexTarget: Int?
    ) {
        val keys = fileIndexesToErrors.filterKeys { it != completeIndexTarget }
        val toRemove = mutableSetOf<Int>()
        keys.forEach { entry ->
            val data = fileIndexesToErrors[entry.key]
            data?.removeIf {
                it.layer == -1 || !it.isValid || !markupModel.containsHighlighter(it)
            }
            if (data?.isEmpty() == true) toRemove.add(entry.key)
        }

        completeIndexTarget?.let {
            fileIndexesToErrors[it]?.addAll(targetErrorHighlighters)
        }
        toRemove.forEach { fileIndexesToErrors.remove(it) }
        val targetPassed = completeIndexTarget in finishedFilesIndexes

        // todo: can it be checked without finishedFilesIndexes?
        fileIndexesToErrors.filter {
            if (it.key != completeIndexTarget) it.value.isNotEmpty() else !targetPassed
        }.keys.also {
            // not yet counted
            if (it.isNotEmpty()) {
                finishedFilesIndexes.removeAll(it)
                notebookLogger().debug("Daemon finished, knownErrorInd: ${fileIndexesToErrors.keys}, recycled errors in ind: $toRemove, remaining: ${it}")
            }
        }
    }

    fun clear() {
        targetErrorHighlighters.clear()
        fileIndexesToErrors.clear()
    }

    override fun dispose() {
        clear()
    }
}