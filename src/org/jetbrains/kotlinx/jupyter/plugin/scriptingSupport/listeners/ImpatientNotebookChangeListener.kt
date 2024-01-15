// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.js.translate.utils.splitToRanges
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.KotlinNotebookAbstractInlayTypeHintsProvider
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadAccess
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.visualization.getCell
import kotlin.math.min


internal enum class NotebookChangeEventsType {
    CELL_ADD,
    CELL_DELETE,
    MARKDOWN_CONVERSION,
    REGULAR
}

internal enum class NotebookMoveEvent {
    CELL_UP,
    CELL_DOWN
}

class ImpatientNotebookChangeListener(
    private val project: Project,
    private val virtualFile: BackedNotebookVirtualFile
) : DocumentListener {
    companion object {
        private const val FAST_INVOCATION_DELTA: Long = 100L
    }

    private var lastTimeCellChangeActionPerformed = 0L
    private var lastStructureChangeEvent: NotebookChangeEventsType? = null
    private var cellsAffectedByReformat = mutableSetOf<Int>()

    private fun handleNotebookChangeEvent(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document)?.let(::BackedNotebookVirtualFile) ?: return

        val (document, psiFile, psiCells) = withReadAccess {
            val document = FileDocumentManager.getInstance().getDocument(file.file)
            val psiFile = file.file.toPsiFile(project)
            val psiCells = psiFile?.getNotebookCells()
            Triple(document, psiFile, psiCells)
        }
        if (document == null || psiFile == null || psiCells == null) return

        val eventType = event.identifyEventChangeType()
        val isCellListChange = eventType.isCellListChangeEvent()
        var isSingleDeleteEvent = eventType == NotebookChangeEventsType.CELL_DELETE
        val isAddEvent = eventType == NotebookChangeEventsType.CELL_ADD

        val documentChangedLineIndex = document.getLineNumber(
            (if (isCellListChange) event.offset + 1 else event.offset).coerceAtMost(event.document.textLength)
        )

        val editor = retrieveEditor(file.file, project)
        val targetCellIndex = editor?.getCell(min(documentChangedLineIndex, editor.document.lineCount - 1))?.ordinal
            ?: run {
                val documentLines = document.text.lines()
                documentLines.take(documentChangedLineIndex).count {
                    it.contains("#%%")
                } - 1
            }

        val cellsSize = psiCells.size
        val maxCellIndex = cellsSize - 1

        val cellOfChange = psiCells.getOrNull(targetCellIndex)

        if (documentChangedLineIndex > document.lineCount - 1 || cellOfChange == null) return // ignore change of whole document
        val notebookDataHolder = NotebookHighlightingService.getForFile(project, virtualFile).dataController
        val isInDocumentReformatAction = notebookDataHolder.reformatDocumentTargets != null
        if (isInDocumentReformatAction) {
            cellsAffectedByReformat.add(targetCellIndex)
            notebookDataHolder.update { reformatDocumentTargets = cellsAffectedByReformat }
            return
        } else cellsAffectedByReformat.clear()

        var cellIndexToStore = when {
            isSingleDeleteEvent -> if (targetCellIndex == maxCellIndex) targetCellIndex - 1 else targetCellIndex
            isAddEvent -> if (targetCellIndex == maxCellIndex) targetCellIndex + 1 else targetCellIndex
            else -> targetCellIndex
        }.coerceAtLeast(0)

        // heuristic on cell move event
        if (isCellListChange) {
            val currentTime = System.currentTimeMillis()
            val last = lastStructureChangeEvent
            val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
            var moveEvent: NotebookMoveEvent? = null
            var moveEventInvokedInCell: Int? = null
            if (currentTime - lastTimeCellChangeActionPerformed < FAST_INVOCATION_DELTA
                // Pattern: ADD, REMOVE
                && last == NotebookChangeEventsType.CELL_ADD && isSingleDeleteEvent) {
                val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
                val ind = cellUnderCaret?.ordinal
                if (ind != null) {
                    val isMoveDown = event.oldFragment.trim().toString() != psiCells.getOrNull(ind - 1)?.text?.trim()
                    if (isMoveDown) { // special case
                        moveEvent = NotebookMoveEvent.CELL_DOWN
                        moveEventInvokedInCell = cellIndexToStore
                        cellIndexToStore += 1
                    } else {
                        moveEvent = NotebookMoveEvent.CELL_UP
                        moveEventInvokedInCell = ind
                    }
                }

                isSingleDeleteEvent = false
            } else {
                lastTimeCellChangeActionPerformed = System.currentTimeMillis()
            }
            val affected = psiCells.indices.filterTo(mutableSetOf()) { it >= cellIndexToStore }
            compilerService.changeCellsData(affected, eventType, moveEvent, moveEventInvokedInCell)
            // no other way to indicate size changed in CaretListener
            notebookDataHolder.notebookDocumentStructureNontrivialChanged.compareAndSet(false, true)

            lastStructureChangeEvent = eventType
        } else lastStructureChangeEvent = null

        val renameRanges = notebookDataHolder.renamingRanges

        cellOfChange.getErrorPresenceIndicator()
            ?.compareAndSet(false, true)
        cellOfChange.invalidateTypeHintsRegistry()

        if (renameRanges == null) {
            notebookDataHolder.update {
                completeHighlightingRange = null
                notebookChangedCellIndex = cellIndexToStore
                if (eventType == NotebookChangeEventsType.REGULAR) {
                    notebookRangesQueuedForHL?.add(cellIndexToStore)
                }
            }
        }
        val targetIndexesAfterAddOrNull = if (isCellListChange) {
            val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
            val s = setOfNotNull(targetCellIndex, cellUnderCaret?.ordinal?.minus(1), cellUnderCaret?.ordinal?.plus(1)).also {
                notebookDataHolder.notebookRangesQueuedForHL?.let { q ->
                    val curInd = cellUnderCaret?.ordinal
                    synchronized(q) {
                        val v = q.toList()
                        q.clear()
                        val shiftedData = v.splitToRanges { if (it > (curInd ?: 0)) (if (isAddEvent) 1 else -1) else 0 }
                            .flatMap { range -> range.first.map { it.plus(range.second) } }
                        q.addAll(shiftedData)
                        q.addAll(it)
                    }
                }
            }
            if (isSingleDeleteEvent) null else s
        } else null
        notebookDataHolder.update {
            notebookDocumentTargetRanges = targetIndexesAfterAddOrNull
        }
    }

    private fun DocumentEvent.identifyEventChangeType(): NotebookChangeEventsType {
        val event = this
        val oldFragment = event.oldFragment
        val newFragment = event.newFragment
        val isNewEmpty = newFragment.isEmpty()
        val isOldEmpty = oldFragment.isEmpty()
        return when {
            ((oldFragment.endsWith(" md") || newFragment.endsWith(" md"))
                    && (isNewEmpty || isOldEmpty)) -> NotebookChangeEventsType.MARKDOWN_CONVERSION
            newFragment.contains("#%%") && isOldEmpty -> NotebookChangeEventsType.CELL_ADD
            oldFragment.contains("#%%") && isNewEmpty -> NotebookChangeEventsType.CELL_DELETE
            else -> NotebookChangeEventsType.REGULAR
        }
    }

    private fun NotebookChangeEventsType.isCellListChangeEvent(): Boolean =
      this == NotebookChangeEventsType.CELL_ADD || this == NotebookChangeEventsType.CELL_DELETE

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}

internal fun PsiLanguageInjectionHost.invalidateTypeHintsRegistry() {
    putUserData(KotlinNotebookAbstractInlayTypeHintsProvider.psiHostChainHintsRegistry, mutableMapOf())
    putUserData(KotlinNotebookAbstractInlayTypeHintsProvider.psiHostHintsRegistry, mutableMapOf())
}

private fun retrieveEditor(vFile: VirtualFile, project: Project): Editor?
    = (FileEditorManager.getInstance(project).getSelectedEditor(vFile) as? JupyterFileEditor)?.editor
