// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.visualization.getCell
import kotlin.math.min

class NotebookCaretListener(private val project: Project, private val vFile: BackedNotebookVirtualFile,
                            private val editor: Editor): CaretListener {
    private val psiFile = vFile.file.toPsiFile(project)
    private var lastCellInd: Int = -1
    private var lastCell: PsiLanguageInjectionHost? = null
    private var prevCell: PsiLanguageInjectionHost? = null
    private var floatingPrevCell: PsiLanguageInjectionHost? = null
    private var floatingCellInd: Int = -1
    private var lastTimeCellFocusChanged = 0L
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    private val doc = psiFile?.toDocument(project)
    private var deferredFastUpdate: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.Default)

    init {
        assert(psiFile != null)
        project.messageBus.connect().subscribe(DAEMON_EVENT_TOPIC, object : DaemonListener {
            override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
                fileEditors.firstOrNull { (it as? TextEditor)?.editor == editor }?.let {
                    floatingPrevCell = null
                    prevCell = null
                    if (floatingCellInd != -1) { // store ind
                        lastCellInd = floatingCellInd
                        floatingCellInd = -1
                    }
                    doc?.putUserData(RenamingEnclosedRange, null)
                    doc?.putUserData(NotebookDocumentTargetRanges, listOfNotNull(lastCell?.textRange))
                }
            }
        })
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
        if (currentTime - lastTimeCellFocusChanged < 600) { // might be reworked
            deferredFastUpdate?.cancel()
            deferredFastUpdate = updateScope.async {
                launch {
                    delay(800)
                    val cells = runReadAction {
                        psiFile.getNotebookCellList()
                    }
                    floatingPrevCell = prevCell ?: cells?.get(lastCellInd)
                    if (prevCell == null) {
                        floatingCellInd = ord
                    }
                    lastCell = cells?.get(ord)

                    val curRange = lastCell?.textRange
                    val floatingRange = floatingPrevCell?.textRange
                    if (curRange != null && floatingRange?.equalsToRange(curRange.startOffset, curRange.endOffset) == true) {
                        floatingPrevCell = null
                    }
                    performRangedUpdate(curRange, listOfNotNull(curRange, floatingPrevCell?.textRange))
                }
            }
            //println("should not trigger an event! for cell $ord")
        } else {
            lastCellInd = ord
            floatingPrevCell = null
            prevCell = lastCell
            lastCell = psiFile.getNotebookCellList()?.let {
                it[
                    if (lastCellInd >= it.size) it.lastIndex
                    else lastCellInd
                ]
            }
            val prevRange = prevCell?.textRange
            val currRange = lastCell?.textRange
            performRangedUpdate(currRange, listOfNotNull(prevRange, currRange))
            //println("Cell focus changed to $ord")
        }
        lastTimeCellFocusChanged = System.currentTimeMillis()
        super.caretPositionChanged(event)
    }

    private fun performRangedUpdate(completeAnalysisRange: TextRange?, reducedRanges: Collection<TextRange>?) {
        doc?.putUserData(NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges, reducedRanges)
        doc?.putUserData(NotebookHighlightingUtilityObject.CompleteHighlightingRange, completeAnalysisRange)
        //println("doc: $doc, putting complete analysis as ${lastCell?.textRange}, text: ${lastCell?.text}")
        psiFile?.let {
            invokeLater {
                codeAnalyzer.restart(it)
            }
        }
    }
}

