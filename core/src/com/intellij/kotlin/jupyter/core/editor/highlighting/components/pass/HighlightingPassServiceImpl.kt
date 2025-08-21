// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.MarkUpModelErrorsHighlightersTracker
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.topic.DocumentCellsStructureChangedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.DaemonIterationState
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.DaemonState
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.NotebookPassProgressTracker
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEventsQueue
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.disposeOfHighlighters
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.async
import org.jetbrains.kotlin.psi.KtFile

/**
 * Manages the lifecycle of highlighting passes, processes and tracks events, and determines files
 * or ranges that require rehighlighting.
 *
 * @see com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingFileManager
 * @see highlightingEventsQueue Event queue for managing and combining document-layer events relevant to highlighting.
 */
internal class HighlightingPassServiceImpl(
    project: Project,
    private val highlightingEventsQueue: HighlightingEventsQueue,
    notebookName: String,
) : HighlightingPassService, HighlightingComponent() {
    companion object {
        private val LOG = notebookLogger()
    }
    private val coroutineScope = KotlinNotebookPluginScope.global
        .childScope("HighlightingPassService for $notebookName")
    private val passStatusIndicator = DaemonIterationState()
    internal val markUpErrorsTracker = child {
        MarkUpModelErrorsHighlightersTracker()
    }
    private val passProgressTracker = child {
        NotebookPassProgressTracker()
    }

    init {
      project.messageBus.connect(this).subscribe(
          DocumentCellsStructureChangedListener.TOPIC, DocumentCellsStructureChangedListener { editor, affectedCellIndex ->
              // means our old indices are useless
              markUpErrorsTracker.clear()
          }
      )
    }

    override val currentPassConfiguration: NotebookPassConfiguration
        get() = passProgressTracker.passConfiguration
    override val passState: DaemonState
        get() = passStatusIndicator.get()

    override fun getRangesToHighlight(file: PsiFile, editor: Editor): Collection<TextRange> {
        val caretOffSet = editor.caretModel.offset
        val cellUnderEditor = editor.getCell(editor.document.getLineNumber(caretOffSet))
        val focusCellIndex = cellUnderEditor.ordinal
        val cells = try {
            file.getNotebookCells()
        } catch (_: IllegalStateException) {
            LOG.warn("Cannot get notebook cells for file: ${file.name}")
            return emptyList()
        }

        val mergedEvent = highlightingEventsQueue.pullEvents()
        val changedCells = mergedEvent?.changedCells ?: emptyList()

        try {
            val targetRanges = changedCells.mapNotNullTo(mutableSetOf()) {
                cells.getOrNull(it)?.textRange
            } + setOfNotNull(
                cells.getOrNull(focusCellIndex)?.textRange
            )

            // pass can be started earlier than call back about the daemon end could fire
            val inProgress = passStatusIndicator.isInProgress
            if (!passStatusIndicator.enterSetupPhase() && !inProgress || inProgress) {
                LOG.debug("Another pass is in setup, aborting, state: ${passStatusIndicator.get()}")
                return targetRanges
            }

            markUpErrorsTracker.resetState(focusCellIndex, false)
            val targetIndexes = buildSet {
                addAll(changedCells)
                add(focusCellIndex)
            }
            passProgressTracker.passStarting(file, focusCellIndex, targetIndexes, cells)
            passStatusIndicator.enterProgressPhase()

            return targetRanges
        } catch (ex: ProcessCanceledException) {
            // abort and save
            if (mergedEvent != null) {
                highlightingEventsQueue.pushEvent(mergedEvent)
            }
            throw ex
        }
    }

    override fun passFinished(editor: Editor, file: PsiFile) {
        if (editor !is EditorEx || passStatusIndicator.isIdle) return

        coroutineScope.async {
            processDaemonFinished(editor, file)
        }
    }

    override fun shouldHighlightErrorsInFile(ktFile: KtFile): Boolean {
        return passProgressTracker.passConfiguration.targetKtFile == ktFile
    }

    @RequiresBackgroundThread
    private fun processDaemonFinished(editor: EditorEx, psiFile: PsiFile?) {
        val passConfiguration = passProgressTracker.passConfiguration

        if (passStatusIndicator.isIdle) {
            return
        }

        // can be cas
        passStatusIndicator.setIdle()
        markUpErrorsTracker.removeHighlightersOutSideOfFocus(passConfiguration.focusCell)

        val remaining = determineIndexesLeftToHighlight(editor)

        val finishedFiles = passConfiguration.cellIndexesToHighlight - remaining

        val project = editor.project
        if (project == null) {
            LOG.info("Project is null for editor $editor in file: $psiFile")
            return
        }
        val isLeft = remaining.isNotEmpty()

        if (isLeft) {
            val event = HighlightingEvent(
                passConfiguration.focusCell,
                null,
                remaining,
                isCustomEditorEvent = true
            )
            highlightingEventsQueue.pushEvent(event)
        }
        LOG.debug("Reducing queue by $finishedFiles, left: $remaining")
    }

    private fun determineIndexesLeftToHighlight(editor: EditorEx): Set<Int> {
        val passRemains = passProgressTracker.getStatusAfterPassFinished(editor)
        disposeOfHighlighters(passRemains.errorHighlightersOutsideOfFocus)

        return passRemains.indexesLeftToProcess
    }
}