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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook


internal fun PsiFile?.getNotebookCellList() =
    (this?.children?.first() as? JupyterNotebook)?.psiCellList

internal fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)

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
        val editorManager = FileEditorManager.getInstance(project) ?: return

        assertBackedNotebook(file)
        val (document, psiFile, psiCells) = runReadAction {
            val d = FileDocumentManager.getInstance().getDocument(file)
            val psiFile = file.toPsiFile(project)
            val psiCells = psiFile?.getNotebookCellList()
            Triple(d, psiFile, psiCells)
        }
        if (document == null || psiFile == null || document.textLength == event.offset) return

        val lineOfChange = document.getLineNumber(event.offset)
        val allLines = document.text.lines()
        val neededCellIndex = allLines.take(lineOfChange).count {
            it.contains("#%%")
        }
        val cellOfChange = psiCells?.get(neededCellIndex - 1)

        if (lineOfChange > allLines.size - 1 || cellOfChange == null) return // ignore change of whole document

        runReadAction {
            val injectedPsi = injectedManager.getInjectedPsiFiles(cellOfChange)?.firstOrNull()?.first

            injectedPsi?.putUserData(ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY, null)
            //psiCells[0]?.putCopyableUserData(ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY, true)
            document.putUserData(NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE, cellOfChange.textRange)
            document.putUserData(NOTEBOOK_FILE_ANALYSIS_DONE_KEY, null)
            //println("Inside before change for ${injectedPsi?.containingFile?.name}, hostsSize: $hostSize, injected: ${injectedPsi?.text}")
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}