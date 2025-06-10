// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.typing.state

import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookCaretMovementEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookCaretMovementProcessor
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookDaemonFinishedEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookDaemonFinishedEventProcessor
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookHighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingManager
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.getErrorPresenceIndicator
import com.intellij.kotlin.jupyter.core.editor.typing.NotebookCellHighlightingTrigger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.toDocument
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.tryWithWriteLock
import com.intellij.kotlin.jupyter.core.util.withWriteLock
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.runIf
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.min

internal class NotebookCaretStateProcessor(
    val editor: Editor,
    project: Project,
    private val notebookHighlightingManager: NotebookHighlightingManager?,
    private val highlightingStarter: NotebookCellHighlightingTrigger
) : NotebookCaretMovementProcessor, NotebookDaemonFinishedEventProcessor {

    private val dataController = notebookHighlightingManager?.dataController
    private val psiFile = editor.virtualFile?.findPsiFile(project)
    private val document = psiFile?.toDocument()
    private val fastUpdateQueueGuardMark = AtomicReference(false)

    private val updateScope = KotlinNotebookPluginScope.global
        .childScope("NotebookCaretStateProcessor for ${editor.virtualFile!!.name}")
    internal val stateLock = ReentrantReadWriteLock()

    private var lastCellInd: Int = -1
    private var lastCell: PsiLanguageInjectionHost? = null
    private var prevCell: PsiLanguageInjectionHost? = null

    private var floatingCellInd: Int = -1
    private var lastTimeCellFocusChanged = 0L
    private var deferredFastUpdate: Job? = null
    private var isFirstRun = true
    private var isSizeChanged = false
    private var lastCellSize = -1

    override fun NotebookCaretMovementEvent.isFastMovement(): Boolean =
        timeHappened - lastTimeCellFocusChanged < fastMovementThreshold

    override fun processEventAdapter(event: NotebookHighlightingEvent) {
        if (event is NotebookDaemonFinishedEvent) {
            onDaemonFinishEvent()
        }
    }

    override fun shouldProcess(event: NotebookHighlightingEvent): Boolean {
        if (event is NotebookDaemonFinishedEvent) return true
        if (event !is NotebookCaretMovementEvent) return false

        val caretEvent = event.event
        if (lastCellInd == -1) {
            lastCellInd = 0
            isSizeChanged = true
            lastTimeCellFocusChanged = System.currentTimeMillis()
            return false
        }
        val cell = editor.getCell(min(caretEvent.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        val isGoodState = !isSizeChanged
        return !((ord == lastCellInd && isGoodState) || isFirstRun)
    }

    override fun processFastCaretMovement(event: NotebookCaretMovementEvent) {
        // might be reworked
        fastUpdateQueueGuardMark.compareAndSet(false, true)
        deferredFastUpdate?.cancel()
        val ordinal = event.cellInterval.ordinal
        deferredFastUpdate = updateScope.async {
            launch {
                val storedFloating = lastCellInd
                delay(500)
                val newOrd = readAction {
                    editor.caretModel.offset.let { document?.getLineNumber(it) }?.let { editor.getCell(it) }
                }
                if (newOrd != null && newOrd.ordinal != ordinal) {
                    floatingCellInd = newOrd.ordinal
                    highlightingStarter.performRangedUpdate(setOfNotNull(ordinal, storedFloating, newOrd.ordinal), this)
                    return@launch
                }
                val storedPrevCell = prevCell

                if (storedPrevCell == null) {
                    floatingCellInd = ordinal
                }
                val cells = readAction {
                    psiFile.getNotebookCells()
                }
                lastCell = cells.getOrNull(ordinal)

                val prevKnownInd = lastCellInd
                val floatingInd = floatingCellInd

                val toStore = if (floatingInd == -1) {
                    storedFloating
                } else floatingInd // concurrent change occurred

                highlightingStarter.performRangedUpdate(setOfNotNull(storedFloating, prevKnownInd, toStore), this)
            }
        }
    }

    override fun processRegularCaretMovement(event: NotebookCaretMovementEvent) {
        val knownPrevInd = lastCellInd
        val ordinal = event.cellInterval.ordinal
        lastCellInd = ordinal
        val errorsRef = lastCell?.getErrorPresenceIndicator()
        prevCell = if (errorsRef?.acquire == true || errorsRef == null) lastCell else null
        val prev = prevCell
        val cells = psiFile.getNotebookCells()
        lastCell = cells.let {
            val prevSize = lastCellSize
            lastCellSize = it.size
            isSizeChanged = prevSize != -1 && prevSize != lastCellSize
            it[
                if (lastCellInd >= it.size) it.lastIndex
                else lastCellInd
            ]
        }
        val prevInd = runIf(prev != null) {
            val indexOfPrev = cells.indexOf(prev)
            if (indexOfPrev == -1) knownPrevInd else indexOfPrev
        }
        val guaranteeAddition = if (knownPrevInd == ordinal) knownPrevInd - 1 else knownPrevInd
        highlightingStarter.performRangedUpdate(setOfNotNull(prevInd, guaranteeAddition, lastCellInd))
    }

    internal inline fun update(action: NotebookCaretStateProcessor.() -> Unit) {
        stateLock.tryWithWriteLock {
            apply(action)
        }
    }

    fun resetState() {
        stateLock.withWriteLock {
            deferredFastUpdate?.cancel()
            floatingCellInd = -1
            isFirstRun = true
            lastCell = null
            prevCell = null
            lastTimeCellFocusChanged = 0L
        }
    }

    private fun onDaemonFinishEvent() {
        // for proper cell move up handle
        val afterNonTrivialChange = dataController?.notebookDocumentStructureNontrivialChanged?.compareAndSet(true, false) == true


        update {
            if (afterNonTrivialChange) {
                isSizeChanged = true
            }

            if (isFirstRun) isFirstRun = false
            prevCell = null
            if (afterNonTrivialChange) {
                val toSwap = dataController?.notebookChangedCellIndex
                    ?: editor.caretModel.offset.let { document?.getLineNumber(it) }?.let { editor.getCell(it).ordinal }
                if (toSwap != null) lastCellInd = toSwap
                floatingCellInd = -1
            } else if (floatingCellInd != -1) { // store ind
                lastCellInd =
                    editor.caretModel.offset.let { document?.getLineNumber(it) }?.let { editor.getCell(it).ordinal } ?: floatingCellInd
                floatingCellInd = -1
            }
            val lastCellIndCopy = lastCellInd

            dataController?.update {
                renamingEnclosedRange = null
                notebookDocumentTargetRanges = listOf(lastCellIndCopy)
                notebookChangedCellIndex = null
            }

            notebookHighlightingManager?.daemonFinished(editor, psiFile)
        }
    }
}