// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.queue

import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent


/**
 * Base interface for storing [com.intellij.openapi.editor.Document] layer events to be
 * further processed by the HL subsystem.
 *
 * Events are added to the queue by [com.intellij.kotlin.jupyter.core.editor.hack.document.publishing.DocumentEventsListener].
 */
internal interface HighlightingEventsQueue {
    /**
     * Adds an event to the queue
     */
    fun pushEvent(event: HighlightingEvent)

    /**
     * Combines all events into one merged event.
     * On completion, this action clears the queue.
     */
    fun pullEvents(): HighlightingEvent?

    /**
     * Cleares the queue.
     */
    fun clear()
}