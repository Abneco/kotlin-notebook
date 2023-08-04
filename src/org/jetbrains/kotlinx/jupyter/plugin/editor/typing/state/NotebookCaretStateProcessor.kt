// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.typing.state

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingManager
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.NotebookCaretHighlightingStarter
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution.JupyterKotlinCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.util.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.tryWithWriteLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.min


interface NotebookCaretMovementProcessor {
    data class EventRelatedData(val event: CaretEvent, val cellInterval: NotebookCellLines.Interval) {
        val timeHappened = System.currentTimeMillis()
    }

    val fastMovementThreshold: Long
        get() = 500

    fun EventRelatedData.isFastMovement(): Boolean

    fun isShouldProcess(event: CaretEvent): Boolean

    fun CaretEvent.getCellOrdinal(): NotebookCellLines.Interval {
        return editor.getCell(min(newPosition.line, editor.document.lineCount - 1))
    }

    fun processEvent(event: CaretEvent) {
        if (!isShouldProcess(event)) return

        val eventRelatedData = EventRelatedData(event, event.getCellOrdinal())

        if (eventRelatedData.isFastMovement()) {
            processFastCaretMovement(eventRelatedData)
        } else processRegularCaretMovement(eventRelatedData)
    }

    fun processFastCaretMovement(event: EventRelatedData)

    fun processRegularCaretMovement(event: EventRelatedData)
}


class NotebookCaretStateProcessor(
    val editor: Editor,
    private val project: Project,
    private val backedNotebookVFile: BackedNotebookVirtualFile,
    private val notebookHighlightingManager: NotebookHighlightingManager?,
    private val highlightingStarter: NotebookCaretHighlightingStarter
) : NotebookCaretMovementProcessor {
    companion object {
        private val LOG = thisLogger()
    }

    private val dataController = notebookHighlightingManager?.dataController
    private val psiFile = editor.virtualFile.toPsiFile(project)
    private val document = psiFile?.toDocument(project)
    private val codeAnalyzerStatusService = DaemonCodeAnalyzerStatusService.getInstance(project)
    private val fastUpdateQueueGuardMark = AtomicReference(false)

    private val updateScope = CoroutineScope(Dispatchers.Default)
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

    override fun NotebookCaretMovementProcessor.EventRelatedData.isFastMovement(): Boolean =
        timeHappened - lastTimeCellFocusChanged < fastMovementThreshold

    override fun isShouldProcess(event: CaretEvent): Boolean {
        if (lastCellInd == -1) {
            lastCellInd = 0
            isSizeChanged = true
            lastTimeCellFocusChanged = System.currentTimeMillis()
            return false
        }
        val cell = editor.getCell(min(event.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        val isGoodState = !isSizeChanged
        return !((ord == lastCellInd && isGoodState) || isFirstRun)
    }

    override fun processFastCaretMovement(event: NotebookCaretMovementProcessor.EventRelatedData) {
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
                    psiFile.getNotebookCellList()
                }
                lastCell = cells?.get(ordinal)

                val prevKnownInd = lastCellInd
                val floatingInd = floatingCellInd

                val toStore = if (floatingInd == -1) {
                    storedFloating
                } else floatingInd // concurrent change occurred

                highlightingStarter.performRangedUpdate(setOfNotNull(storedFloating, prevKnownInd, toStore), this)
            }
        }
    }

    override fun processRegularCaretMovement(event: NotebookCaretMovementProcessor.EventRelatedData) {
        val knownPrevInd = lastCellInd
        val ordinal = event.cellInterval.ordinal
        lastCellInd = ordinal
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

    internal fun onDaemonFinishEvent() {
        // for proper cell move up handle
        val afterNonTrivialChange = dataController?.notebookDocumentStructureNontrivialChanged?.compareAndSet(true, false) == true
        val isRunning = codeAnalyzerStatusService.daemonRunning


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

            // move to the daemonListener?
            val queue = dataController?.notebookRangesQueuedForHL
            val finished = notebookHighlightingManager?.finishedHighlighting
            val target = notebookHighlightingManager?.completeRangeInd
            // we don't want to lose any updates happened during concurrent modification or delay
            val isCanModifyHLRequests = notebookHighlightingManager?.isCanModifyHLRequestAfterExecution(project) == true
            if (queue != null && !finished.isNullOrEmpty() && isCanModifyHLRequests) {
                queue.removeAll(finished)
            }
            if (!isRunning) {
                val executionRequestsDone =
                    JupyterKotlinCellExecutionCallbackFactory.getInstance()
                        .daemonFinished(backedNotebookVFile, finished, queue, isCanModifyHLRequests)
                LOG.debug("Reducing queue by $finished, canModify: ${isCanModifyHLRequests}, exec requests done: $executionRequestsDone")
                if (notebookHighlightingManager?.daemonFinished(editor, psiFile, queue, executionRequestsDone) == true) {
                    queue?.clear()
                }
            }
            queue?.addIfNotNull(target) ?: Unit
        }
    }
}