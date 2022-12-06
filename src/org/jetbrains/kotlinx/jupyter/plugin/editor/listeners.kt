// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.visualization.getCell
import kotlin.math.min

class NotebookCaretListener(private val project: Project, private val vFile: VirtualFile,
                            private val editor: Editor): CaretListener {
    private val psiFile = vFile.toPsiFile(project)
    private var lastCellInd: Int = -1
    private var lastCell: PsiLanguageInjectionHost? = null
    private var lastTimeCellFocusChanged = 0L
    private val compilerService = JupyterCompilerService.getForFile(project, vFile)
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    private val doc = psiFile?.toDocument(project)

    init {
        assert(isBackedNotebook(vFile))
        assert(psiFile != null)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        if (lastCellInd == -1) {
            lastCellInd = 0
            lastTimeCellFocusChanged = System.currentTimeMillis()
            return
        }
        val cell = editor.getCell(min(event.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        if (ord == lastCellInd) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTimeCellFocusChanged < 600) {
            //println("should not trigger an event! for cell $ord")
        } else {
            lastCellInd = ord
            lastCell = psiFile.getNotebookCellList()?.get(lastCellInd)
            compilerService.completeAnalysisCellTarget = lastCell
            doc?.putUserData(NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE, null) // global
            psiFile?.let {
                invokeLater {
                    codeAnalyzer.restart(it) // think of recycling
                }
            }
            //println("Cell focus changed to $ord")
        }
        lastTimeCellFocusChanged = System.currentTimeMillis()
        super.caretPositionChanged(event)
    }
}

