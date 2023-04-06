// Copyright 2000-2022 JetBrains s .r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.openapi.application.ApplicationManager
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
import org.jetbrains.kotlin.js.translate.utils.splitToRanges
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
import kotlin.math.min


internal enum class NotebookChangeEventsType {
    CELL_ADD,
    CELL_DELETE,
    MARKDOWN_CONVERSION,
    REGULAR
}

class ImpatientNotebookChangeListener(
    private val project: Project,
    private val virtualFile: BackedNotebookVirtualFile
) : DocumentListener {
    companion object {
        private const val FAST_INVOCATION_DELTA: Long = 200L
        private val sampleTextRangeRef = TextRange(1, 1)
        private inline fun <T> withReadAccess(crossinline block: () -> T): T {
            return if (ApplicationManager.getApplication().isDispatchThread) {
                block()
            } else runReadAction {
                block()
            }
        }
    }

    private var lastTimeCellChangeActionPerformed = 0L
    private var lastAdjustedRange: TextRange? = null
    private var cellsAffectedByReformat = mutableSetOf<Int>()

    private fun handleNotebookChangeEvent(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document)?.let(::BackedNotebookVirtualFile) ?: return

        val (document, psiFile, psiCells) = withReadAccess {
            val document = FileDocumentManager.getInstance().getDocument(file.file)
            val psiFile = file.file.toPsiFile(project)
            val psiCells = psiFile?.getNotebookCellList()
            Triple(document, psiFile, psiCells)
        }
        if (document == null || psiFile == null || psiCells == null) return

        val eventType = event.identifyEventChangeType()
        val isCellListChange = eventType.isCellListChangeEvent()
        var isSingleDeleteEvent = eventType == NotebookChangeEventsType.CELL_DELETE
        val isAddEvent = eventType == NotebookChangeEventsType.CELL_ADD

        val changedLineIndex = document.getLineNumber(
            (if (isCellListChange) event.offset + 1 else event.offset).coerceAtMost(event.document.textLength)
        )

        val editor = retrieveEditor(file.file, project)
        val neededCellIndex = editor?.getCell(min(changedLineIndex, editor.document.lineCount - 1))?.ordinal
            ?: run {
                val documentLines = document.text.lines()
                documentLines.take(changedLineIndex).count {
                    it.contains("#%%")
                } - 1
            }

        val cellsSize = psiCells.size
        val maxCellIndex = cellsSize - 1

        val cellOfChange = psiCells.getOrNull(neededCellIndex)

        if (changedLineIndex > document.lineCount - 1 || cellOfChange == null) return // ignore change of whole document
        val isInDocumentReformatAction = synchronized(document) {
            document.getUserData(ReformatDocumentActionTargets) != null
        }
        if (isInDocumentReformatAction) {
            cellsAffectedByReformat.add(neededCellIndex)
            document.handleWholeRefactorAction(cellsAffectedByReformat)
            return
        } else cellsAffectedByReformat.clear()

        var properCellIndexToStore = when {
            isSingleDeleteEvent -> if (neededCellIndex == maxCellIndex) neededCellIndex - 1 else neededCellIndex
            isAddEvent -> if (neededCellIndex == maxCellIndex) neededCellIndex + 1 else neededCellIndex
            else -> neededCellIndex
        }.coerceAtLeast(0)

        // heuristic on cell move event
        if (isCellListChange) {
            val currentTime = System.currentTimeMillis()
            val last = lastAdjustedRange
            if (currentTime - lastTimeCellChangeActionPerformed < FAST_INVOCATION_DELTA && last != null) {
                val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
                val ind = cellUnderCaret?.ordinal
                if (ind != null) {
                    JupyterCompilerService.getForFile(project, virtualFile).swapCellsData(ind - 1, ind - 2, psiCells)
                    val isMoveDown = event.oldFragment.trim().toString() != psiCells.getOrNull(ind - 1)?.text?.trim()
                    if (isMoveDown) {
                        properCellIndexToStore += 1
                    }
                }

                isSingleDeleteEvent = false
            } else {
                lastTimeCellChangeActionPerformed = System.currentTimeMillis()
            }
            // no other way to indicate size changed in CaretListener
            document.getUserData(NotebookDocumentStructureNontrivialChanged)?.compareAndSet(false, true)

            lastAdjustedRange = sampleTextRangeRef
        } else lastAdjustedRange = null

        val renameRange = synchronized(document) { document.getUserData(RenamingEnclosedRange) }

        cellOfChange.getErrorPresenceIndicator()
            ?.compareAndSet(false, true)

        if (renameRange == null) {
            document.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, properCellIndexToStore)
            document.putUserData(CompleteHighlightingRange, null)
            if (eventType == NotebookChangeEventsType.REGULAR) {
                document.getUserData(NotebookQueuedTargetRanges)?.add(properCellIndexToStore)
            }
        }
        val targetIndexesAfterAddOrNull = if (isCellListChange) {
            val cellUnderCaret = editor?.caretModel?.offset?.let { document.getLineNumber(it) }?.let { editor.getCell(it) }
            val s = setOfNotNull(neededCellIndex, cellUnderCaret?.ordinal?.minus(1), cellUnderCaret?.ordinal?.plus(1)).also {
                document.getUserData(NotebookQueuedTargetRanges)?.let { q ->
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
        document.putUserData(NotebookDocumentTargetRanges, targetIndexesAfterAddOrNull)
        cellOfChange.invalidateTypeHintsRegistry()
    }

    private fun DocumentEvent.identifyEventChangeType(): NotebookChangeEventsType {
        val event = this
        val oldFragment = event.oldFragment
        val newFragment = event.newFragment
        val isNewEmpty = newFragment.isEmpty()
        val isOldEmpty = oldFragment.isEmpty()
        return when {
            ((oldFragment.contains(" md") || newFragment.contains(" md"))
                    && (isNewEmpty || isOldEmpty)) -> NotebookChangeEventsType.MARKDOWN_CONVERSION
            newFragment.contains("#%%") && isOldEmpty -> NotebookChangeEventsType.CELL_ADD
            oldFragment.contains("#%%") && isNewEmpty -> NotebookChangeEventsType.CELL_DELETE
            else -> NotebookChangeEventsType.REGULAR
        }
    }

    private fun Document.handleWholeRefactorAction(targets: MutableSet<Int>) {
        putUserData(ReformatDocumentActionTargets, targets)
    }

    private fun NotebookChangeEventsType.isCellListChangeEvent(): Boolean =
        this == NotebookChangeEventsType.CELL_ADD || this == NotebookChangeEventsType.CELL_DELETE

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}

private fun retrieveEditor(vFile: VirtualFile, project: Project): Editor?
    = (FileEditorManager.getInstance(project).getSelectedEditor(vFile) as? JupyterFileEditor)?.editor
