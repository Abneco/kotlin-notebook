// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.utils.ifEmpty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


/**
 * Contract for providing meta-data related to Notebook highlighting.
 * Implementations should be [thread-safe].
 * Configuration should be done via help of [NotebookFileHighlightingDataConfigurator].
 *
 * @see NotebookPerFileHighlightingMetaDataController
 * @see NotebookHighlightingManager
 */
internal interface NotebookFileHighlightingDataProvider {
    val completeHighlightingRange: TextRange?
    val notebookChangedCellIndex: Int?
    val renamingRanges: Collection<TextRange>?
    val notebookRangesQueuedForHL: MutableSet<Int>?
    val notebookDocumentTargetRanges: MutableSet<Int>?
    val reformatDocumentTargets: MutableSet<Int>?
    val lastExecutedCellsBatch: Set<Int>?
    val notebookDocumentStructureNontrivialChanged: AtomicReference<Boolean>
    val notebookCellsUpdatesAllowedToChange: AtomicReference<Boolean>
}


class NotebookPerFileHighlightingMetaDataController(
    private val notebookFile: BackedNotebookVirtualFile,
    val executionHighlightingHelper: NotebookCellExecutionHighlightingHelper,
    parentDisposable: Disposable
): NotebookFileHighlightingDataProvider, Disposable {
    private enum class KeysValues {
        CompleteRange,
        ChangedCellIndex,
        TargetRanges,
        RangesQueuedForHL,
        ReformatTargets,
        RenamingTargets,
        StructureNonTrivialChanged,
        CellsUpdatesAllowedToChange
    }

    private val dataStorage = ConcurrentHashMap<KeysValues, Any>()

    private fun initialiseStorage() {
        dataStorage[KeysValues.StructureNonTrivialChanged] = AtomicReference(false)
        dataStorage[KeysValues.CellsUpdatesAllowedToChange] = AtomicReference(true)
        dataStorage[KeysValues.ReformatTargets] = mutableSetOf<TextRange>()
        dataStorage[KeysValues.TargetRanges] = mutableSetOf<TextRange>()
        dataStorage[KeysValues.RenamingTargets] = mutableSetOf<TextRange>()
        dataStorage[KeysValues.RangesQueuedForHL] = mutableSetOf<TextRange>()
    }

    init {
        Disposer.register(parentDisposable, this)
        initialiseStorage()
    }

    inner class NotebookHighlightingMetaDataConfigurator(
        val notebookDocumentStructureNontrivialChanged: AtomicReference<Boolean>
    ) {
        var completeHighlightingRange: TextRange? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.completeHighlightingRange
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.CompleteRange] = value
                } else dataStorage.remove(KeysValues.CompleteRange)
            }

        var notebookChangedCellIndex: Int? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.notebookChangedCellIndex
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.ChangedCellIndex] = value
                } else dataStorage.remove(KeysValues.ChangedCellIndex)
            }

        var renamingEnclosedRange: Collection<TextRange>? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.renamingRanges
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.RenamingTargets] = value
                } else dataStorage.remove(KeysValues.RenamingTargets)
            }

        var notebookDocumentTargetRanges: Collection<Int>? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.notebookDocumentTargetRanges
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.TargetRanges] = value
                } else dataStorage.remove(KeysValues.TargetRanges)
            }

        var notebookRangesQueuedForHL: MutableSet<Int>? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.notebookRangesQueuedForHL
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.RangesQueuedForHL] = value
                } else dataStorage.remove(KeysValues.RangesQueuedForHL)
            }

        var reformatDocumentTargets: MutableSet<Int>? = null
            get() = this@NotebookPerFileHighlightingMetaDataController.reformatDocumentTargets
            set(value) {
                field = value
                if (value != null) {
                    dataStorage[KeysValues.ReformatTargets] = value
                } else dataStorage.remove(KeysValues.ReformatTargets)
            }
    }

    @Suppress("UNCHECKED_CAST")
    private val dataHolder = NotebookHighlightingMetaDataConfigurator(
        dataStorage[KeysValues.StructureNonTrivialChanged] as AtomicReference<Boolean>
    )

    fun update(action: (NotebookHighlightingMetaDataConfigurator).() -> Unit) {
        dataHolder.apply(action)
    }

    fun invalidateStateAfterCellExecution(executedCellInd: Int? = null) {
        dataStorage.remove(KeysValues.TargetRanges)
        dataStorage.remove(KeysValues.RenamingTargets)
        dataStorage.remove(KeysValues.CompleteRange)
        if (executedCellInd != null) {
            dataStorage[KeysValues.ChangedCellIndex] = executedCellInd
        } else dataStorage.remove(KeysValues.ChangedCellIndex)
    }

    override val completeHighlightingRange: TextRange?
        get() = dataStorage[KeysValues.CompleteRange] as? TextRange

    override val notebookChangedCellIndex: Int?
        get() = dataStorage[KeysValues.ChangedCellIndex] as? Int

    @Suppress("UNCHECKED_CAST")
    override val renamingRanges: Collection<TextRange>?
        get() = dataStorage.getOrElse(KeysValues.RenamingTargets) { null } as? Collection<TextRange>

    @Suppress("UNCHECKED_CAST")
    override val notebookRangesQueuedForHL: MutableSet<Int>?
        get() = (dataStorage[KeysValues.RangesQueuedForHL] as? MutableSet<Int>)

    @Suppress("UNCHECKED_CAST")
    override val notebookDocumentTargetRanges: MutableSet<Int>?
        get() = (dataStorage[KeysValues.TargetRanges] as? MutableSet<Int>)?.ifEmpty { return null }

    @Suppress("UNCHECKED_CAST")
    override val reformatDocumentTargets: MutableSet<Int>?
        get() = (dataStorage[KeysValues.ReformatTargets] as? MutableSet<Int>)?.ifEmpty { return null }

    override val lastExecutedCellsBatch: Set<Int>
        get() = executionHighlightingHelper.getLastExecutedCellsBatch()

    @Suppress("UNCHECKED_CAST")
    override val notebookDocumentStructureNontrivialChanged: AtomicReference<Boolean>
        get() = (dataStorage[KeysValues.StructureNonTrivialChanged] as AtomicReference<Boolean>)

    @Suppress("UNCHECKED_CAST")
    override val notebookCellsUpdatesAllowedToChange: AtomicReference<Boolean>
        get() = (dataStorage[KeysValues.CellsUpdatesAllowedToChange] as AtomicReference<Boolean>)

    override fun dispose() {
        dataStorage.clear()
    }
}