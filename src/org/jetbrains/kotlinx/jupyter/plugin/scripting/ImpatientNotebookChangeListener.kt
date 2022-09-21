// Copyright 2000-2022 JetBrains s .r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY
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
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook

class ImpatientNotebookChangeListener(
    private val project: Project,
    private val virtualFile: VirtualFile
): DocumentListener {
    init {
        assertBackedNotebook(virtualFile)
        runReadAction {
            virtualFile.toPsiFile()
                ?.getNotebookCellList()?.firstOrNull()
                ?.putCopyableUserData(ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY, true)
        }
    }
    private val injectedManager = InjectedLanguageManager.getInstance(project)

    private fun handleNotebookChangeEvent(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        val editorManager = FileEditorManager.getInstance(project) ?: return

        assertBackedNotebook(file)
        val (document, psiFile, psiCells) = runReadAction {
            val d = FileDocumentManager.getInstance().getDocument(file)
            val psiFile = file.toPsiFile()
            val psiCells = psiFile?.getNotebookCellList()
            Triple(d, psiFile, psiCells)
        }
        if (document == null || psiFile == null) return

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
            psiCells[0]?.putCopyableUserData(ANALYZER_PASS_INJECTION_IGNORED_HOST_KEY, true)
            //println("Inside before change for ${injectedPsi?.containingFile?.name}, hostsSize: $hostSize, injected: ${injectedPsi?.text}")
        }
    }

    private fun PsiFile?.getNotebookCellList() =
        (this?.children?.first() as? JupyterNotebook)?.psiCellList

    private fun VirtualFile.toPsiFile(): PsiFile? =
        PsiManager.getInstance(project).findFile(this)

    override fun beforeDocumentChange(event: DocumentEvent) {
        handleNotebookChangeEvent(event)
    }
}