// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.getAllIntervalPointers
import com.intellij.jupyter.execution.listeners.NotebookSessionEventListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.DaemonIterationState
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.HighlightingPassStateTracker
import com.intellij.kotlin.jupyter.core.editor.typing.NotebookCaretListener
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookAfterScriptsUpdatePluginAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.ImpatientNotebookChangeListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.createDisposableChild
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.isCurrentlySelectedInEditor
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Main project-level service to manipulate Highlighting in Kotlin Notebook.
 * Manipulation includes restarting HL, applying special style for cells out of focus, i.e., Shadowing,
 * and keeping track of applied highlighters to [MarkupModelEx]
 */
class NotebookHighlightingManager(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    childScope: CoroutineScope,
    var completeRangeInd: Int?
): NotebookPerFileChildService(virtualFile, childScope) {
    companion object {
        private val LOG = notebookLogger()
    }
    private val document by lazy {
        withReadAccess {
            FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
        }
    }

    private val iterationLock = Mutex(false)
    // todo: fields can be lazily initialized?
    private val iterationStateIndicator = DaemonIterationState()
    private val highlightingPassStateTracker = createDisposableChild {
        HighlightingPassStateTracker(project)
    }

    private var _jupyterFile: PsiFile? = null
    val jupyterPsiFile: PsiFile? get() = _jupyterFile

    val dataController: NotebookPerFileHighlightingMetaDataController = createDisposableChild {
        NotebookPerFileHighlightingMetaDataController(
            project,
            virtualFile,
        )
    }

    private suspend fun updateData(cellsIndices: List<Int>) {
        iterationLock.withLock {
            dataController.update {
                notebookRangesQueuedForHL = mutableSetOf<Int>().apply { addAll(cellsIndices) }
                notebookDocumentStructureNontrivialChanged.set(false)
                renamingEnclosedRange = null
                notebookDocumentTargetRanges = null
            }
        }
    }

    @RequiresReadLock
    private fun getAllCellsIndexes(): List<Int>? {
        ThreadingAssertions.assertReadAccess()
        val currentVFile = virtualFile.file
        val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(currentVFile) as? TextEditor ?: return null
        val editorState = textEditor.editor
        val cells = getAllIntervalPointers(editorState).mapNotNull { it.get()?.ordinal }
        return cells
    }

    private fun Disposable.addListeners() {
        // todo: move to document layer
        document.addDocumentListener(
            ImpatientNotebookChangeListener(project, virtualFile),
            this
        )
        addNotebookSessionEventListener()
        addNotebookScriptsStateListener()
    }

    private fun addNotebookSessionEventListener() {
        val targetFile = virtualFile
        project.messageBus.connect(this).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun sessionStarted(virtualFile: BackedNotebookVirtualFile, isAfterRestart: Boolean) {
                    if (targetFile != virtualFile || !isAfterRestart) return

                    restartAnalysing()
                }
            }
        )
    }

    private fun addNotebookScriptsStateListener() {
        project.messageBus.connect(this).subscribe(
            NotebookScriptsStateListener.TOPIC,
            createPluginModeAwareInstance(
                ::createK1Instance,
                ::createK2Instance
            )
        )
    }

    private fun createK1Instance() : NotebookAfterScriptsUpdatePluginAwareHandler {
        return NotebookAfterScriptsUpdatePluginAwareHandler { file, _ ->
            if (file != virtualFile) return@NotebookAfterScriptsUpdatePluginAwareHandler
        }
    }

    /**
     * Since shadowing does not work, just perform complete restart after the main execution effect took place
     */
    private fun createK2Instance() : NotebookAfterScriptsUpdatePluginAwareHandler {
        return NotebookAfterScriptsUpdatePluginAwareHandler { file, _ ->
            if (file != virtualFile) return@NotebookAfterScriptsUpdatePluginAwareHandler

            val isCurrentFileOpened = virtualFile.isCurrentlySelectedInEditor(project)
            if (isCurrentFileOpened) {
                restartAnalysing()
            }
        }
    }

    init {
        coroutineScope.async {
            initializeService()
        }
    }

    private suspend fun initializeService() {
        val cells = readAction {
            getAllCellsIndexes()
        }
        if (cells == null) {
            LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
        } else {
            updateData(cells)
        }

        readAction {
            _jupyterFile = virtualFile.file.findPsiFile(project)
            addListeners()
        }
    }

    private var activeCaretListener: NotebookCaretListener? = null

    fun tryGetKnownHostFor(file: PsiFile): PsiLanguageInjectionHost? {
        if (file !is KtFile) return null
        return highlightingPassStateTracker.getInjectionHost(file)
    }

    fun isFileTarget(file: PsiFile): Boolean {
        return highlightingPassStateTracker.isFileTarget(file)
    }

    fun associateWithNewCaretListener(listener: NotebookCaretListener, editor: Editor) {
        activeCaretListener = listener
        highlightingPassStateTracker.editorCreated(editor)
    }

    fun passCreated(targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        if (cells == null) {
            LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
        }

        // pass can be started earlier than call back about the daemon end could fire
        if (!iterationStateIndicator.enterSetupPhase() && !iterationStateIndicator.isInProgress) {
            LOG.debug("Another pass is in setup, aborting, state: ${iterationStateIndicator.get()}")
            return
        }

        try {
            clearState()
            //context.myHighlightingPassStateTracker.passCreated()
            highlightingPassStateTracker.passCreated(targetIndexes, cells, completeRangeInd)
            iterationStateIndicator.enterProgressPhase()
            this.completeRangeInd = completeRangeInd
        } catch (ex: Exception) {
            if (ex !is ProcessCanceledException) {
                LOG.warn("Exception during pass creation: ", ex)
            }
            iterationStateIndicator.setIdle()
            throw ex
        }
    }

    fun finishedAnalysisForFile(psiFile: PsiFile): Deferred<Unit> = coroutineScope.async {
        highlightingPassStateTracker.injectedFileProcessed(psiFile)
    }

    fun daemonFinished(editor: Editor, psiFile: PsiFile?) {
        val markup = (editor as? EditorEx)?.filteredDocumentMarkupModel ?: return
        if (iterationStateIndicator.isIdle) return

        coroutineScope.async {
            if (iterationStateIndicator.isIdle) {
                return@async
            }

            processDaemonFinished(editor, psiFile, markup)
        }
    }

    fun editorPotentiallyDisposed() {
        clearState()
    }

    fun restartAnalysing() {
        coroutineScope.async {
            runCatching {
                val cells = readAction {
                    getAllCellsIndexes()
                }
                if (cells == null) {
                    LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
                    return@async
                }
                // todo: go to doc + combine everything else needed
                updateData(cells)

                NotebookHighlightingRestarter.scheduleRegularUpdate(jupyterPsiFile!!)
            }.onFailure {
                LOG.warn("Problem during restarting analysis for $virtualFile: ", it)
            }
        }
    }

    override fun dispose() {
        clearState(true)
    }

    /**
     * Should be called before each HL pass
     */
    private fun clearState(complete: Boolean = false) {
        highlightingPassStateTracker.clearState(completeRangeInd, complete)
        if (complete) {
            activeCaretListener = null
            _jupyterFile = null
        }
    }

    @RequiresBackgroundThread
    private suspend fun processDaemonFinished(editor: Editor, psiFile: PsiFile?, markup: MarkupModelEx) {
        val queue = dataController.notebookRangesQueuedForHL
        val target = completeRangeInd

        iterationLock.withLock {
            if (iterationStateIndicator.isIdle) {
                return@withLock
            }

            // can be cas
            iterationStateIndicator.setIdle()
            highlightingPassStateTracker.disposeErrorHighlighters(target)

            val remaining = highlightingPassStateTracker
                .determineCellIndexesLeftToHighlight(
                    queue,
                    jupyterPsiFile,
                    markup,
                    target
                )

            val finishedFiles = highlightingPassStateTracker.finishedFiles
            reduceQueue(finishedFiles)
            queue?.addIfNotNull(target)

            val executionRequestsDone = dataController
                .executionHighlightingHelper
                .daemonFinished(finishedFiles, queue)

            val project = editor.project
            if (project == null) {
                LOG.info("Project is null for editor $editor in file: $psiFile")
                return
            }

            val isLeft = remaining.isNotEmpty()

            if (isLeft || !executionRequestsDone) {
                psiFile?.let { psi ->
                    NotebookHighlightingRestarter.scheduleRegularUpdate(psi)
                }
            }
            if (isLeft) {
                queue?.addAll(remaining)
                dataController.update {
                    notebookRangesQueuedForHL = queue
                }
            }
            LOG.debug("Reducing queue by $finishedFiles, left: $remaining, exec requests done: $executionRequestsDone")
        }
    }

    private fun reduceQueue(finishedFiles: Set<Int>) {
        val queue = dataController.notebookRangesQueuedForHL
        // we don't want to lose any updates happened during concurrent modification or delay
        if (queue != null && finishedFiles.isNotEmpty()) {
            queue.removeAll(finishedFiles)
        }
    }
}
