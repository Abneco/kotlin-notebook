// Copyright 2000-2022 JetBrains s .r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.CompleteHighlightingRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentStructureNontrivialChanged
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookQueuedTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.ReformatDocumentActionTargets
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.file.invalidateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference


internal enum class NotebookChangeEventsType {
    CELL_LIST_ADD_EVENT,
    CELL_LIST_DELETE_EVENT,
    MARKDOWN_CONVERSION_EVENT,
    REGULAR
}

class ImpatientNotebookChangeListener(
    private val project: Project,
    private val virtualFile: BackedNotebookVirtualFile
): DocumentListener {
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private var lastTimeCellChangeActionPerformed = 0L
    private var lastAdjustedRange: TextRange? = null
    private var cellsAffectedByReformat = mutableSetOf<Int>()
    init {
        FileDocumentManager.getInstance().getDocument(virtualFile.file)?.let {
            it.putUserData(NotebookDocumentStructureNontrivialChanged, AtomicReference(false))
            it.putUserData(NotebookQueuedTargetRanges, mutableSetOf())
        }
    }

    private fun handleNotebookChangeEvent(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document)?.let(::BackedNotebookVirtualFile) ?: return

        val (document, psiFile, psiCells) = runReadAction {
            val d = FileDocumentManager.getInstance().getDocument(file.file)
            val psiFile = file.file.toPsiFile(project)
            val psiCells = psiFile?.getNotebookCellList()
            Triple(d, psiFile, psiCells)
        }
        if (document == null || psiFile == null) return
        val lineOfChange = document.getLineNumber(event.offset)
        val allLines = document.text.lines()
        val neededCellIndex = allLines.take(lineOfChange).count {
            it.contains("#%%")
        }
        val editor = document.retrieveEditor(file.file, project)

        //println("old needed cell ind: #$neededCellIndex, new: ${editor?.getCell(min(lineOfChange, editor.document.lineCount - 1))}")
        val cellSize = psiCells?.size ?: 0

        val eventsType = event.identifyEventChangeType()
        val isCellListChange = eventsType.isCellListChangeEvent()
        var isSingleDeleteEvent = eventsType == NotebookChangeEventsType.CELL_LIST_DELETE_EVENT
        val isAddEvent = eventsType == NotebookChangeEventsType.CELL_LIST_ADD_EVENT

        val actualCellIndex = if (eventsType
            == NotebookChangeEventsType.MARKDOWN_CONVERSION_EVENT) neededCellIndex
            else if (isSingleDeleteEvent) {
                if (neededCellIndex + 1 < cellSize) neededCellIndex + 1 else neededCellIndex
            }
            else if (neededCellIndex > 0) neededCellIndex - 1 else 0
        val cellOfChange = psiCells?.get(actualCellIndex)

        if (lineOfChange > allLines.size - 1 || cellOfChange == null) return // ignore change of whole document
        val isInDocumentReformatAction = synchronized(document) {
            document.getUserData(ReformatDocumentActionTargets) != null
        }
        if (isInDocumentReformatAction) {
            cellsAffectedByReformat.add(actualCellIndex)
            document.handleWholeRefactorAction(cellsAffectedByReformat)
            return
        } else cellsAffectedByReformat.clear()

        val delta = if (event.newLength > event.oldLength) event.newLength else -event.oldLength

        var properCellIndexOrNull = if (isSingleDeleteEvent) actualCellIndex - 1 else if (isAddEvent) neededCellIndex else actualCellIndex
        val cellRange = cellOfChange.textRange
        var properTextRange
            = if (isCellListChange) TextRange(event.offset, event.offset + event.newLength)
              else cellRange.createSafeTextRangeWithDelta(delta)

        // heuristic on cell move event
        if (isCellListChange) {
            val currentTime = System.currentTimeMillis()
            val last = lastAdjustedRange
            if (currentTime - lastTimeCellChangeActionPerformed < 200 && last != null) {
                val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
                val ind = cellUnderCaret?.ordinal
                if (ind != null) {
                    JupyterCompilerService.getForFile(project, virtualFile).swapCellsData(ind - 1, ind -2, psiCells)
                    val isMoveDown = event.oldFragment.trim().toString() != psiCells.getOrNull(ind - 1)?.text?.trim()
                    if (isMoveDown) {
                        properCellIndexOrNull += 1
                    }
                }

                isSingleDeleteEvent = false
                properTextRange = properTextRange.union(last).createSafeTextRangeWithDelta(delta)
            } else {
                lastTimeCellChangeActionPerformed = System.currentTimeMillis()
            }
            // no other way to indicate size changed in CaretListener
            document.getUserData(NotebookDocumentStructureNontrivialChanged)?.compareAndSet(false, true)

            lastAdjustedRange = properTextRange
        } else lastAdjustedRange = null

        val renameRange = synchronized(document) { document.getUserData(RenamingEnclosedRange) }

        cellOfChange.getErrorPresenceIndicator()
            ?.compareAndSet(false, true)

        if (renameRange == null) {
            document.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, properCellIndexOrNull)
            document.putUserData(CompleteHighlightingRange, null)
            if (eventsType == NotebookChangeEventsType.REGULAR) {
                document.getUserData(NotebookQueuedTargetRanges)?.add(properCellIndexOrNull)
            }
        }
        val targetIndexesAfterAddOrNull = if (isCellListChange) {
            val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
            val s = setOfNotNull(neededCellIndex, cellUnderCaret?.ordinal?.minus(1), cellUnderCaret?.ordinal?.plus(1)).also {
                document.getUserData(NotebookQueuedTargetRanges)?.let { q ->
                    synchronized(q) {
                        val v = q.toList()
                        q.clear()
                        q.addAll(v.map { el -> el.minus(1) })
                        q.addAll(v.map { el -> el.plus(1) })
                        q.addAll(it)
                    }
                }
            }
            if (isSingleDeleteEvent) null else s
        } else null
        document.putUserData(NotebookDocumentTargetRanges, targetIndexesAfterAddOrNull)
        cellOfChange.invalidateTypeHintsRegistry()
    }

    private fun DocumentEvent.identifyEventChangeType(): NotebookChangeEventsType {
        val event = this
        val oldFragment = event.oldFragment
        val newFragment = event.newFragment
        val isNewEmpty = newFragment.isEmpty()
        val isOldEmpty = oldFragment.isEmpty()
        return if ((oldFragment.contains(" md")
                    || newFragment.contains(" md")) && (isNewEmpty || isOldEmpty))
            NotebookChangeEventsType.MARKDOWN_CONVERSION_EVENT
        else if (newFragment.contains("#%%") && isOldEmpty)
            NotebookChangeEventsType.CELL_LIST_ADD_EVENT
        else if (oldFragment.contains("#%%") && isNewEmpty)
            NotebookChangeEventsType.CELL_LIST_DELETE_EVENT
        else NotebookChangeEventsType.REGULAR
    }

    private fun invokeHeuristicOnCellMove(targetTextRange: TextRange): TextRange {
        val currentTime = System.currentTimeMillis()
        var range = targetTextRange
        val last = lastAdjustedRange
        if (currentTime - lastTimeCellChangeActionPerformed < 200 && last != null) {
            range = range.union(last)
        } else {
            lastTimeCellChangeActionPerformed = System.currentTimeMillis()
        }
        lastAdjustedRange = range
        return range
    }

    private fun Document.handleWholeRefactorAction(targets: MutableSet<Int>) {
        //putUserData(NotebookDocumentTargetRanges, getRangesAfterDocumentReformatOrNull(allCells))
        putUserData(ReformatDocumentActionTargets, targets)
    }

    private fun NotebookChangeEventsType.isCellListChangeEvent(): Boolean =
        this == NotebookChangeEventsType.CELL_LIST_ADD_EVENT || this == NotebookChangeEventsType.CELL_LIST_DELETE_EVENT

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}

internal fun TextRange.createSafeTextRangeWithDelta(delta: Int): TextRange {
    val newEnd = endOffset + delta
    return if (newEnd < startOffset) this else TextRange(startOffset, newEnd)
}

internal fun Document.retrieveEditor(vFile: VirtualFile, project: Project): Editor?
    = (FileEditorManager.getInstance(project).getSelectedEditor(vFile) as? JupyterFileEditor)?.editor

internal fun Document.retrieveLineNumberUnderCaret(vFile: VirtualFile, project: Project): Int? {
    val editor = retrieveEditor(vFile, project)
    val caretOffset = editor?.caretModel?.offset ?: return null
    return getLineNumber(caretOffset)
}
