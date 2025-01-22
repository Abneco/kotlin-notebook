// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.document

import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.hack.queue.HighlightingEventsQueue
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

/**
 * Class is responsible for transforming raw input events
 * into [HighlightingEvent]s.
 */
internal class DocumentInputEventsTransformer(
  private val editor: Editor,
  private val document: Document,
  private val highlightingEventsQueue: HighlightingEventsQueue,
) : HighlightingComponent() {

    inner class NotebookDocumentListener : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            highlightingEventsQueue.pushEvent(
                transformDocumentChange(event)
            )
        }
    }
    inner class NotebookCaretListener : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            highlightingEventsQueue.pushEvent(
                transformCaretMovement(event)
            )
        }
    }

    override fun initializeSelf() {
        addListeners()
    }

    private fun addListeners() {
        document.addDocumentListener(NotebookDocumentListener(), this)
        editor.caretModel.addCaretListener(NotebookCaretListener(), this)
    }

    private fun transformCaretMovement(event: CaretEvent): HighlightingEvent {
        TODO()
    }

    private fun transformDocumentChange(event: DocumentEvent): HighlightingEvent {
        TODO()
    }
}