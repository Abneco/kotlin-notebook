// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.typing

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution.KotlinNotebookCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.util.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.tryWithWriteLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.min


class NotebookCaretListener(
    private val project: Project,
    private val vFile: BackedNotebookVirtualFile,
    private val editor: Editor,
    parentDisposable: Disposable,
): CaretListener, Disposable {
    companion object {
        private val LOG = thisLogger()
    }
    private val psiFile = vFile.file.toPsiFile(project)
    private val doc = psiFile?.toDocument(project)
    private val updateScope = CoroutineScope(Dispatchers.Default)
    private val projectOptionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    private val codeAnalyzerStatus = DaemonCodeAnalyzerStatusService.getInstance(project)
    private val fastUpdateQueueGuardMark = AtomicReference(false)
    private val notebookHighlightingManager =
        psiFile?.virtualFile?.let(BackedNotebookVirtualFile::takeIfBacked)?.let { NotebookHighlightingService.getForFile(project, it) }
    private val dataController = notebookHighlightingManager?.dataController

    private val stateLock = ReentrantReadWriteLock()

    private var lastCellInd: Int = -1
    private var lastCell: PsiLanguageInjectionHost? = null
    private var prevCell: PsiLanguageInjectionHost? = null

    private var floatingCellInd: Int = -1
    private var lastTimeCellFocusChanged = 0L
    private var deferredFastUpdate: Job? = null
    private var isFirstRun = true
    private var isSizeChanged = false
    private var lastCellSize = -1

    init {
        assert(psiFile != null)
        Disposer.register(parentDisposable, this)
        if (dataController == null) {
            LOG.warn("Data controller is null during init, manager: $notebookHighlightingManager")
        }
        notebookHighlightingManager?.associateWithNewCaretListener(this, editor)

        project.messageBus.connect(this).subscribe(DAEMON_EVENT_TOPIC, object : DaemonListener {
            private val scriptDefManager = ScriptDefinitionsManager.getInstance(project)

            override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                fileEditors.firstOrNull { (it as? TextEditor)?.editor == editor }?.let {
                    if (!scriptDefManager.isReady()) {
                        return
                    }
                    // for proper cell move up handle
                    val afterNonTrivialChange = dataController?.notebookDocumentStructureNontrivialChanged?.compareAndSet(true, false) == true
                    if (afterNonTrivialChange) {
                        isSizeChanged = true
                    }

                    stateLock.tryWithWriteLock {
                        if (isFirstRun) isFirstRun = false
                        prevCell = null
                        val isRunning = codeAnalyzerStatus.daemonRunning
                        if (afterNonTrivialChange) {
                            val toSwap = dataController?.notebookChangedCellIndex
                                ?: editor.caretModel.offset.let { doc?.getLineNumber(it) }?.let { editor.getCell(it).ordinal }
                            if (toSwap != null) lastCellInd = toSwap
                            floatingCellInd = -1
                        } else if (floatingCellInd != -1) { // store ind
                            lastCellInd = editor.caretModel.offset.let { doc?.getLineNumber(it) }?.let { editor.getCell(it).ordinal } ?: floatingCellInd
                            floatingCellInd = -1
                        }
                        val lastCellIndCopy = lastCellInd


                        dataController?.update {
                            renamingEnclosedRange = null
                            notebookDocumentTargetRanges = listOf(lastCellIndCopy)
                            notebookChangedCellIndex = null
                        }

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
                                KotlinNotebookCellExecutionCallbackFactory.getInstance()
                                    .daemonFinished(vFile, finished, queue, isCanModifyHLRequests)
                            LOG.debug("Reducing queue by $finished, canModify: ${isCanModifyHLRequests}, exec requests done: $executionRequestsDone")
                            if (notebookHighlightingManager?.daemonFinished(editor, psiFile, queue, executionRequestsDone) == true) {
                                queue?.clear()
                            }
                        }
                        queue?.addIfNotNull(target) ?: Unit
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
        if (currentTime - lastTimeCellFocusChanged < 500) { // might be reworked
            fastUpdateQueueGuardMark.compareAndSet(false, true)
            deferredFastUpdate?.cancel()
            deferredFastUpdate = updateScope.async {
                launch {
                    val storedFloating = lastCellInd
                    delay(500)
                    val newOrd = readAction {
                        editor.caretModel.offset.let { doc?.getLineNumber(it) }?.let { editor.getCell(it) }
                    }
                    if (newOrd != null && newOrd.ordinal != ord) {
                        floatingCellInd = newOrd.ordinal
                        performRangedUpdate(setOfNotNull(ord, storedFloating, newOrd.ordinal), this)
                        return@launch
                    }
                    val storedPrevCell = prevCell

                    if (storedPrevCell == null) {
                        floatingCellInd = ord
                    }
                    val cells = readAction {
                        psiFile.getNotebookCellList()
                    }
                    lastCell = cells?.get(ord)

                    val prevKnownInd = lastCellInd
                    val floatingInd = floatingCellInd

                    val toStore = if (floatingInd == -1) {
                        storedFloating
                    } else floatingInd // concurrent change occurred

                    performRangedUpdate(setOfNotNull(storedFloating, prevKnownInd, toStore), this)
                }
            }
        } else {
            val knownPrevInd = lastCellInd
            lastCellInd = ord
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
        }
        lastTimeCellFocusChanged = System.currentTimeMillis()
    }

    override fun dispose() {
        deferredFastUpdate?.cancel()
        updateScope.cancel()
        notebookHighlightingManager?.editorPotentiallyDisposed()
    }

    private fun performRangedUpdate(reducedIndexes: Collection<Int>, context: CoroutineScope? = null) {
        dataController?.update {
            notebookDocumentTargetRanges = reducedIndexes
            notebookChangedCellIndex = reducedIndexes.last()
            notebookRangesQueuedForHL?.addAll(reducedIndexes)
        }
        psiFile?.let {
            if (projectOptionsProvider.state.shouldLimitTypeHintsByActiveCell) {
                InlayHintsPassFactory.clearModificationStamp(editor)
            }
            context?.launch {
                readAction {
                    codeAnalyzer.restart(it)
                }
            } // only because it's EDT
            ?: codeAnalyzer.restart(it)
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
}

