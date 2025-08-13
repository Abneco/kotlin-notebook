// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue


/**
 * Base interface for storing [com.intellij.openapi.editor.Document] layer events to be
 * further processed by the HL subsystem.
 *
 * Events are added to the queue by [com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.DocumentInputEventsTransformerAdapter].
 */
internal interface HighlightingEventsQueue {
    /**
     * Adds an event to the queue.
     */
    fun pushEvent(event: HighlightingEvent)

    /**
     * Combines all pending events into a single merged event.
     * Returns null if the queue is empty.
     * On completion, this action clears the queue.
     */
    fun pullEvents(): HighlightingEvent?

    /**
     * Clears the queue.
     */
    fun clear()
}