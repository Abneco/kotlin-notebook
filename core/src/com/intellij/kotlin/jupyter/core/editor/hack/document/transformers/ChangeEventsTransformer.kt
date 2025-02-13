// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.document.transformers

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookChangeEventsType
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
internal class ChangeEventsTransformer(
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
        val isCellListChange = eventType.isCellListChangeEvent()

        val documentChangedLineIndex = backedDocumentFile.getLineNumber(
            (if (isCellListChange) rawEvent.offset + 1 else rawEvent.offset).coerceAtMost(rawEvent.document.textLength)
        )
        val targetCellIndex = editor.getCell(min(documentChangedLineIndex, editor.document.lineCount - 1)).ordinal

        if (documentChangedLineIndex > backedDocumentFile.lineCount - 1) return null // ignore change of the whole document

        return HighlightingEvent(
            targetCellIndex,
            null,
            setOf(targetCellIndex)
        )
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
}