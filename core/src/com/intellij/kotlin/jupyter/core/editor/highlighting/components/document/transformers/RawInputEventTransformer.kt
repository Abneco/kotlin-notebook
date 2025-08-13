// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent

/**
 * Utility interface for transforming arbitrary input events and producing [HighlightingEvent].
 */
internal fun interface RawInputEventTransformer<T> {
    fun transformRawInput(rawEvent: T): HighlightingEvent?
}