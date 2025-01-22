// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.openapi.util.TextRange
import java.util.concurrent.atomic.AtomicReference

/**
 * Contract for providing meta-data related to Notebook highlighting.
 * Implementations should be [thread-safe].
 * Configuration should be done via help of [NotebookFileHighlightingDataConfigurator].
 *
 * @see NotebookPerFileHighlightingMetaDataController
 * @see NotebookHighlightingManager
 */
// todo: split by layers
internal interface NotebookFileHighlightingDataProvider {
    /**
     * todo: comments for each property
     */
    val completeHighlightingRange: TextRange?
    val notebookChangedCellIndex: Int?
    // todo: this is actually queuedRanges, remove
    val renamingRanges: Collection<TextRange>?
    val notebookRangesQueuedForHL: MutableSet<Int>?
    val notebookDocumentTargetRanges: MutableSet<Int>?
    // todo: not needed
    val reformatDocumentTargets: MutableSet<Int>?
    // todo: input event, not a part of [cells]
    val lastExecutedCellsBatch: Set<Int>?
    // todo: can be transformed
    val notebookDocumentStructureNontrivialChanged: AtomicReference<Boolean>
    // todo: should not be a part of data
    val notebookCellsUpdatesAllowedToChange: AtomicReference<Boolean>
}