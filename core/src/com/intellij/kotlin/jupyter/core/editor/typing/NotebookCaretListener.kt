// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.typing

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookCaretMovementEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService.Companion.getHighlightingManagerForFile
import com.intellij.kotlin.jupyter.core.editor.typing.daemon.NotebookHighlightingDaemonListener
import com.intellij.kotlin.jupyter.core.editor.typing.state.NotebookCaretStateProcessor
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface NotebookCellHighlightingTrigger {
    fun performRangedUpdate(reducedIndexes: Collection<Int>, context: CoroutineScope? = null)
}


class NotebookCaretListener(
    private val project: Project,
    vFile: BackedNotebookVirtualFile,
    private val editor: Editor,
    parentDisposable: Disposable,
): CaretListener, NotebookCellHighlightingTrigger, Disposable {
    companion object {
        private val LOG = notebookLogger()
    }
    private val projectOptionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    private val notebookHighlightingManager = vFile.file.getHighlightingManagerForFile(project)
    private val dataController = notebookHighlightingManager?.dataController
    private val caretStateProcessor = NotebookCaretStateProcessor(editor, project, notebookHighlightingManager, this)

    init {
        assert(notebookHighlightingManager != null)
        Disposer.register(parentDisposable, this)
        if (dataController == null) {
            LOG.warn("Data controller is null during init, manager: $notebookHighlightingManager")
        }
        notebookHighlightingManager?.associateWithNewCaretListener(this, editor)
        project.messageBus
            .connect(this)
            .subscribe(DAEMON_EVENT_TOPIC,
                       NotebookHighlightingDaemonListener(caretStateProcessor)
            )
    }

    override fun caretPositionChanged(event: CaretEvent) {
        with(caretStateProcessor) {
            onEventHappened(NotebookCaretMovementEvent(event, event.getCellOrdinal()))
        }
    }

    override fun dispose() {
        notebookHighlightingManager?.editorPotentiallyDisposed()
    }

    override fun performRangedUpdate(reducedIndexes: Collection<Int>, context: CoroutineScope?) {
        dataController?.update {
            notebookDocumentTargetRanges = reducedIndexes
            notebookChangedCellIndex = reducedIndexes.last()
            notebookRangesQueuedForHL?.addAll(reducedIndexes)
        }
        notebookHighlightingManager?.jupyterPsiFile?.let {
            if (projectOptionsProvider.shouldLimitTypeHintsByActiveCell) {
                InlayHintsPassFactoryInternal.clearModificationStamp(editor)
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
        caretStateProcessor.resetState()
    }
}

