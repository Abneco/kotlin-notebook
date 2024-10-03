// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.components

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.markup.MarkupModelListenerPluginAwareProvider
import java.util.concurrent.ConcurrentHashMap


/**
 * Manages error highlighting within editors, including adding markup listeners and tracking error indices.
 *
 * This class's main goal is to keep track of files which have left error highlighters and dispose them, if Shadowing is required.
 */
internal class ErrorHighlighterComponent(
    parentDisposable: Disposable,
    private val sharedLogger: Logger
) : Disposable {
    init {
        Disposer.register(parentDisposable, this)
    }

    val knownErrorIndices = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    private val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()

    private lateinit var activeMarkupModelListener: MarkupModelListener

    fun addMarkupListener(editor: Editor) {
        activeMarkupModelListener = MarkupModelListenerPluginAwareProvider
            .provideListener(targetErrorHighlighters)

        val editorEx = editor as? EditorEx ?: return
        val suitableParent = (editor as? EditorImpl)?.disposable ?: this
        editorEx
            .filteredDocumentMarkupModel
            .addMarkupModelListener(suitableParent, activeMarkupModelListener)
    }


    fun determineFilesWithLeftErrors(
        markupModel: MarkupModelEx,
        finishedFiles: MutableSet<Int>,
        completeIndexTarget: Int?
    ) {
        val keys = knownErrorIndices.filterKeys { it != completeIndexTarget }
        val toRemove = mutableSetOf<Int>()
        keys.forEach { entry ->
            val data = knownErrorIndices[entry.key]
            data?.removeIf {
                it.layer == -1 || !it.isValid || !markupModel.containsHighlighter(it)
            }
            if (data?.isEmpty() == true) toRemove.add(entry.key)
        }

        completeIndexTarget?.let {
            knownErrorIndices[it]?.addAll(targetErrorHighlighters)
        }
        toRemove.forEach { knownErrorIndices.remove(it) }
        val targetPassed = completeIndexTarget in finishedFiles

        knownErrorIndices.filter {
            if (it.key != completeIndexTarget) it.value.isNotEmpty() else !targetPassed
        }.keys.also {
            // not yet counted
            if (it.isNotEmpty()) {
                finishedFiles.removeAll(it)
                sharedLogger.debug("Daemon finished, knownErrorInd: ${knownErrorIndices.keys}, recycled errors in ind: $toRemove, remaining: ${it}")
            }
        }
    }

    fun clear() {
        targetErrorHighlighters.clear()
        knownErrorIndices.clear()
    }

    override fun dispose() {
        clear()
    }
}