// Copyright 2000-2022 JetBrains s .r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_FILE_ANALYSIS_DONE_KEY
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook


internal enum class NotebookChangeEventsType {
    CELL_LIST_CHANGE_EVENT,
    MARKDOWN_CONVERSION_EVENT,
    REGULAR
}

class ImpatientNotebookChangeListener(
    private val project: Project,
    private val virtualFile: VirtualFile
): DocumentListener {
    init {
        assertBackedNotebook(virtualFile)
    }
    private val injectedManager = InjectedLanguageManager.getInstance(project)

    private fun handleNotebookChangeEvent(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        assertBackedNotebook(file)
        val (document, psiFile, psiCells) = runReadAction {
            val d = FileDocumentManager.getInstance().getDocument(file)
            val psiFile = file.toPsiFile(project)
            val psiCells = psiFile?.getNotebookCellList()
            Triple(d, psiFile, psiCells)
        }
        if (document == null || psiFile == null || document.textLength == event.offset) return
        //println("Inside exec before doc changed")
        val lineOfChange = document.getLineNumber(event.offset)
        val allLines = document.text.lines()
        val neededCellIndex = allLines.take(lineOfChange).count {
            it.contains("#%%")
        }

        val eventsType = event.identifyEventChangeType()
        val isMdEvent = eventsType == NotebookChangeEventsType.MARKDOWN_CONVERSION_EVENT
        val actualCellIndex = if (isMdEvent) neededCellIndex else if (neededCellIndex > 0) neededCellIndex - 1 else 0
        val cellOfChange = psiCells?.get(actualCellIndex)

        if (lineOfChange > allLines.size - 1 || cellOfChange == null) return // ignore change of whole document
        val delta = if (event.newLength > event.oldLength) event.newLength else -event.oldLength

        val properCellIndexOrNull = if (eventsType != NotebookChangeEventsType.CELL_LIST_CHANGE_EVENT) actualCellIndex else null
        runReadAction {
            val injectedPsi = injectedManager.getInjectedPsiFiles(cellOfChange)?.firstOrNull()?.first

            injectedPsi?.putUserData(ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY, null)
            //psiCells[0]?.putCopyableUserData(ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY, true)
            document.putUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE,
                                 TextRange(cellOfChange.textRange.startOffset, cellOfChange.textRange.endOffset + delta))
            document.putUserData(NOTEBOOK_FILE_ANALYSIS_DONE_KEY, null)
            document.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, properCellIndexOrNull)
            cellOfChange.putUserData(KotlinNotebookAbstractInlayTypeHintsProvider.psiHostHintsRegistry, mutableMapOf())
            //println("Inside before change for ${injectedPsi?.containingFile?.name}, hostsSize: $hostSize, injected: ${injectedPsi?.text}")
        }
    }
    private fun DocumentEvent.identifyEventChangeType(): NotebookChangeEventsType {
        val event = this
        val oldFragment = event.oldFragment
        val newFragment = event.newFragment
        return if ((oldFragment.contains(" md")
                    || newFragment.contains(" md")) && (newFragment.isEmpty() || oldFragment.isEmpty()))
            NotebookChangeEventsType.MARKDOWN_CONVERSION_EVENT
        else if (oldFragment.contains("#%%") || newFragment.contains("#%%"))
            NotebookChangeEventsType.CELL_LIST_CHANGE_EVENT
        else NotebookChangeEventsType.REGULAR
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}