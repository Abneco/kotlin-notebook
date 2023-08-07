// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.typing

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.NotebookCaretMovementEvent
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService.Companion.getHighlightingManagerForFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.daemon.NotebookHighlightingDaemonListener
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.state.NotebookCaretStateProcessor
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

interface NotebookCellHighlightingTrigger {
    fun performRangedUpdate(reducedIndexes: Collection<Int>, context: CoroutineScope? = null)
}


class NotebookCaretListener(
    private val project: Project,
    private val vFile: BackedNotebookVirtualFile,
    private val editor: Editor,
    parentDisposable: Disposable,
): CaretListener, NotebookCellHighlightingTrigger, Disposable {
    companion object {
        private val LOG = thisLogger()
    }
    private val psiFile = vFile.file.toPsiFile(project)
    private val projectOptionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
    private val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    private val notebookHighlightingManager = psiFile?.virtualFile?.getHighlightingManagerForFile(project)
    private val dataController = notebookHighlightingManager?.dataController
    private val caretStateProcessor = NotebookCaretStateProcessor(editor, project, vFile, notebookHighlightingManager, this)

    init {
        assert(psiFile != null)
        Disposer.register(parentDisposable, this)
        if (dataController == null) {
            LOG.warn("Data controller is null during init, manager: $notebookHighlightingManager")
        }
        notebookHighlightingManager?.associateWithNewCaretListener(this, editor)
        project.messageBus
            .connect(this)
            .subscribe(DAEMON_EVENT_TOPIC,
                       NotebookHighlightingDaemonListener(project, caretStateProcessor)
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
        psiFile?.let {
            if (projectOptionsProvider.shouldLimitTypeHintsByActiveCell) {
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
        caretStateProcessor.resetState()
    }
}

