// Copyright 2000-2022 JetBrains s .r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.NOTEBOOK_FILE_ANALYSIS_DONE_KEY
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook


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
        val cellOfChange = psiCells?.get(if (neededCellIndex > 0) neededCellIndex - 1 else 0)

        if (lineOfChange > allLines.size - 1 || cellOfChange == null) return // ignore change of whole document
        val delta = if (event.newLength > event.oldLength) event.newLength else -event.oldLength

        runReadAction {
            val injectedPsi = injectedManager.getInjectedPsiFiles(cellOfChange)?.firstOrNull()?.first

            injectedPsi?.putUserData(ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY, null)
            //psiCells[0]?.putCopyableUserData(ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY, true)
            document.putUserData(NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE,
                                 TextRange(cellOfChange.textRange.startOffset, cellOfChange.textRange.endOffset + delta))
            document.putUserData(NOTEBOOK_FILE_ANALYSIS_DONE_KEY, null)
            cellOfChange.putUserData(KotlinNotebookAbstractInlayTypeHintsProvider.psiHostHintsRegistry, mutableMapOf())
            //println("Inside before change for ${injectedPsi?.containingFile?.name}, hostsSize: $hostSize, injected: ${injectedPsi?.text}")
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}