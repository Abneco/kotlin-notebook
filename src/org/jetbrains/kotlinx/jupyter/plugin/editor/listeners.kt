// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentStructureNontrivialChanged
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookQueuedTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.min

internal enum class DaemonState {
    Started, Finished, Aborted
}


class NotebookCaretListener(private val project: Project, private val vFile: BackedNotebookVirtualFile,
                            private val editor: Editor): CaretListener {
    private val psiFile = vFile.file.toPsiFile(project)
    private val doc = psiFile?.toDocument(project)
    private val updateScope = CoroutineScope(Dispatchers.Default)
    private val projectOptionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)

    private val stateLock = ReentrantReadWriteLock()

    private var lastCellInd: Int = -1
    private var lastCell: PsiLanguageInjectionHost? = null
    private var prevCell: PsiLanguageInjectionHost? = null
    private var floatingPrevCell: PsiLanguageInjectionHost? = null
    private var floatingCellInd: Int = -1
    private var lastTimeCellFocusChanged = 0L
    private var deferredFastUpdate: Job? = null
    private var isFirstRun = true
    private var isSizeChanged = false
    private var lastCellSize = -1

    init {
        assert(psiFile != null)
        doc?.putUserData(NotebookCellsUpdatesAllowedToChange, AtomicReference(true))
        project.messageBus.connect().subscribe(DAEMON_EVENT_TOPIC, object : DaemonListener {
            private val scriptDefManager = ScriptDefinitionsManager.getInstance(project)
            override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
                fileEditors.firstOrNull { (it as? TextEditor)?.editor == editor }?.let {
                    if (!scriptDefManager.isReady()) {
                        return
                    }

                    // for proper cell move up handle
                    val afterNonTrivialChange = doc?.getUserData(NotebookDocumentStructureNontrivialChanged)?.compareAndSet(true, false) == true
                    if (afterNonTrivialChange) {
                        isSizeChanged = true
                    }

                    //stateLock.write { state = DaemonState.Finished }
                    if (isFirstRun) isFirstRun = false
                    floatingPrevCell = null
                    prevCell = null
                    if (afterNonTrivialChange) {
                        val toSwap = doc?.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)
                            ?: editor.caretModel.offset.let { doc?.getLineNumber(it) }?.let { editor.getCell(it).ordinal }
                        if (toSwap != null) lastCellInd = toSwap
                        floatingCellInd = -1
                    } else if (floatingCellInd != -1) { // store ind
                        lastCellInd = floatingCellInd
                        floatingCellInd = -1
                    }
                    val isAfterRenaming = doc?.getUserData(RenamingEnclosedRange) != null
                    stateLock.write {
                        doc?.putUserData(RenamingEnclosedRange, null)
                        //doc?.putUserData(NotebookDocumentTargetRanges, listOfNotNull(lastCell?.textRange))
                        doc?.putUserData(NotebookDocumentTargetRanges, listOf(lastCellInd))
                        doc?.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
                        doc?.getUserData(NotebookQueuedTargetRanges)?.clear()
                        if (doc?.getUserData(NotebookCellsUpdatesAllowedToChange)?.get() == true) {
                            JupyterKotlinCellExecutionCallbackFactory.getInstance().daemonFinished(vFile)
                        }
                    }
                    if (isAfterRenaming) {
                        lastCellInd = 0
                    }
                }
            }

        })
    }

    override fun caretPositionChanged(event: CaretEvent) {
        if (lastCellInd == -1) {
            lastCellInd = 0
            isSizeChanged = true
            lastTimeCellFocusChanged = System.currentTimeMillis()
            return
        }
        val cell = editor.getCell(min(event.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        val isGoodState = !isSizeChanged
        if ((ord == lastCellInd && isGoodState) || isFirstRun) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTimeCellFocusChanged < 600) { // might be reworked
            deferredFastUpdate?.cancel()
            deferredFastUpdate = updateScope.async {
                launch {
                    val storedFloating = lastCellInd
                    delay(600)
                    val newOrd = runReadAction {
                        editor.caretModel.offset.let { doc?.getLineNumber(it) }?.let { editor.getCell(it) }
                    }
                    if (storedFloating == newOrd?.ordinal && ord != storedFloating) return@launch
                    if (newOrd != null && newOrd.ordinal != ord) {
                        floatingCellInd = newOrd.ordinal
                        performRangedUpdate(setOfNotNull(ord, storedFloating, newOrd.ordinal))
                    }

                    if (prevCell == null) {
                        floatingCellInd = ord
                    }
                    val cells = runReadAction {
                        psiFile.getNotebookCellList()
                    }
                    floatingPrevCell = prevCell ?: cells?.get(lastCellInd)
                    lastCell = cells?.get(ord)

                    val prevKnownInd = lastCellInd
                    val floatingInd = floatingCellInd
                    if (prevKnownInd == floatingInd) {
                        floatingPrevCell = null
                    }
                    val toStore = if (floatingInd == -1) {
                        storedFloating
                    } else floatingInd // concurrent change occurred

                    performRangedUpdate(setOfNotNull(storedFloating, prevKnownInd, toStore))
                }
            }
            //println("should not trigger an event! for cell $ord")
        } else {
            val knownPrevInd = lastCellInd
            lastCellInd = ord
            floatingPrevCell = null
            val errorsRef = lastCell?.getErrorPresenceIndicator()
            prevCell = if (errorsRef?.acquire == true || errorsRef == null) lastCell else null
            val prev = prevCell
            val cells = psiFile.getNotebookCellList()
            lastCell = cells?.let {
                val prevSize = lastCellSize
                lastCellSize = it.size
                isSizeChanged = prevSize != -1 && prevSize != lastCellSize
                it[
                    if (lastCellInd >= it.size) it.lastIndex
                    else lastCellInd
                ]
            }
            val prevInd = runIf(prev != null) {
                cells?.indexOf(prev)?.let { if (it == -1) knownPrevInd else it }
            }
            val guaranteeAddition = if (knownPrevInd == ord) knownPrevInd - 1 else knownPrevInd
            performRangedUpdate(setOfNotNull(prevInd, guaranteeAddition, lastCellInd))
            //println("Cell focus changed to $ord")
        }
        lastTimeCellFocusChanged = System.currentTimeMillis()
    }

    private fun performRangedUpdate(reducedIndexes: Collection<Int>) {
        doc?.putUserData(NotebookDocumentTargetRanges, reducedIndexes)
        //doc?.putUserData(NotebookHighlightingUtilityObject.CompleteHighlightingRange, completeAnalysisRange)
        doc?.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, reducedIndexes.last())
        doc?.getUserData(NotebookQueuedTargetRanges)?.addAll(reducedIndexes)
        //println("doc: $doc, putting complete analysis as ${lastCell?.textRange}, text: ${lastCell?.text}")
        psiFile?.let {
            if (projectOptionsProvider.state.shouldLimitTypeHintsByActiveCell) {
                InlayHintsPassFactory.clearModificationStamp(editor)
            }
            invokeLater {
                codeAnalyzer.restart(it)
            }
        }
    }
}

