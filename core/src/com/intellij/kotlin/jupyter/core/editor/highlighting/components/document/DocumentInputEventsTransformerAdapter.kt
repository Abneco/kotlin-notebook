// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.document

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers.CaretMovementEventTransformer
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers.DocumentChangeEventsTransformer
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers.RawInputEventTransformer
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEventsQueue
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

/**
 * Class is responsible for adapting [RawInputEventTransformer]s and populating the [HighlightingEventsQueue]
 * with [HighlightingEvent]s.
 */
internal class DocumentInputEventsTransformerAdapter(
    private val editor: Editor,
    private val document: Document,
    private val highlightingEventsQueue: HighlightingEventsQueue,
) : HighlightingComponent() {
    inner class NotebookDocumentListener : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            if (event.document != document) return

            val transformedEvent = documentChangeEventTransformer.transformRawInput(event) ?: return
            highlightingEventsQueue.pushEvent(
                transformedEvent
            )
        }
    }
    inner class NotebookCaretListener : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            val caretEvent = caretMovementEventTransformer.transformRawInput(event) ?: return
            highlightingEventsQueue.pushEvent(
                caretEvent
            )
        }
    }

    private val caretMovementEventTransformer = child {
        CaretMovementEventTransformer(editor)
    }
    private val documentChangeEventTransformer = child {
        DocumentChangeEventsTransformer(editor)
    }

    /**
     * We do create this component by event trigger, so it should be initialized right away.
     */
    init {
      initialize()
    }

    override fun initializeSelf() {
        addListeners()
    }

    private fun addListeners() {
        document.addDocumentListener(NotebookDocumentListener(), this)
        editor.caretModel.addCaretListener(NotebookCaretListener(), this)
    }
}