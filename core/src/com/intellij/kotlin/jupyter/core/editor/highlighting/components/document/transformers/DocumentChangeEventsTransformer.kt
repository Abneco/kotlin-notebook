// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.topic.DocumentCellsStructureChangedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent
import com.intellij.kotlin.jupyter.core.util.NotebookChangeEventType
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlin.math.min

/**
 * Transformer to determine particular notebook cell indexes changed during [DocumentEvent].
 *
 * Should be registered as child [HighlightingComponent].
 */
internal class DocumentChangeEventsTransformer(
    private val editor: Editor
) : RawInputEventTransformer<DocumentEvent>, HighlightingComponent() {
    override fun transformRawInput(rawEvent: DocumentEvent): HighlightingEvent? {
        val eventVirtualFile = FileDocumentManager.getInstance().getFile(rawEvent.document)
        if (eventVirtualFile == null) {
            return null
        }

        val backedNotebookVirtualFile = BackedNotebookVirtualFile.takeBackend(eventVirtualFile)

        val backedDocumentFile = withReadAccess {
            FileDocumentManager.getInstance().getDocument(backedNotebookVirtualFile.file)
        }
        if (backedDocumentFile == null) return null

        val eventType = rawEvent.identifyEventChangeType()
        val notebookStructureChanged = eventType.isNotebookStructureChanged()

        val documentChangedLineIndex = backedDocumentFile.getLineNumber(
            // +1 for the case when the structure is changed as it does not contain the line break
            (if (notebookStructureChanged) rawEvent.offset + 1 else rawEvent.offset).coerceAtMost(rawEvent.document.textLength)
        )
        // lineCount - 1 is required to skip json-related extra line
        val affectedCellIndex = editor.getCell(min(documentChangedLineIndex, editor.document.lineCount - 1)).ordinal

        if (documentChangedLineIndex > backedDocumentFile.lineCount - 1) return null // ignore change of the whole document

        val changedCells = setOf(affectedCellIndex)
        if (notebookStructureChanged) {
            editor.project?.messageBus?.syncPublisher(DocumentCellsStructureChangedListener.TOPIC)
                ?.cellsChanged(editor, affectedCellIndex)
        }

        return HighlightingEvent(
            affectedCellIndex,
            null,
            changedCells,
            isCustomEditorEvent = false
        )
    }

    private fun DocumentEvent.identifyEventChangeType(): NotebookChangeEventType {
        val event = this
        val oldFragment = event.oldFragment
        val newFragment = event.newFragment
        val isNewEmpty = newFragment.isEmpty()
        val isOldEmpty = oldFragment.isEmpty()
        return when {
            ((oldFragment.endsWith(" md") || newFragment.endsWith(" md"))
                    && (isNewEmpty || isOldEmpty)) -> NotebookChangeEventType.MARKDOWN_CONVERSION
            newFragment.contains("#%%") && isOldEmpty -> NotebookChangeEventType.CELL_ADD
            oldFragment.contains("#%%") && isNewEmpty -> NotebookChangeEventType.CELL_DELETE
            else -> NotebookChangeEventType.REGULAR
        }
    }

    private fun NotebookChangeEventType.isNotebookStructureChanged(): Boolean =
        this == NotebookChangeEventType.CELL_ADD || this == NotebookChangeEventType.CELL_DELETE
}