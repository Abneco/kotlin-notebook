// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.getAllIntervalPointers
import com.intellij.jupyter.execution.listeners.NotebookSessionEventListener
import com.intellij.kotlin.jupyter.core.editor.hack.document.DocumentInputEventsTransformer
import com.intellij.kotlin.jupyter.core.editor.hack.editor.NotebookEditorCreatedListener
import com.intellij.kotlin.jupyter.core.editor.hack.pass.HighlightingPassServiceImpl
import com.intellij.kotlin.jupyter.core.editor.hack.queue.HighlightingEventsQueueImpl
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingRestarter
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookPerFileHighlightingMetaDataController
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.DaemonIterationState
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.HighlightingPassStateTracker
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookAfterScriptsUpdatePluginAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.createDisposableChild
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.isCurrentlySelectedInEditor
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.notebooks.visualization.getCellByOffset
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.psi.KtFile

private data class EditorCells(
    val focusCell: Int,
    val allCellOrdinals: List<Int>,
)


/**
 * Main project-level service to manipulate Highlighting in Kotlin Notebook.
 * Manipulation includes restarting HL, applying special style for cells out of focus, i.e., Shadowing,
 * and keeping track of applied highlighters to [MarkupModelEx]
 */
class NotebookHighlightingFileManager(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    childScope: CoroutineScope,
    var completeRangeInd: Int?
): NotebookPerFileChildService(virtualFile, childScope) {
    companion object {
        private val LOG = notebookLogger()
    }
    inner class EditorCreatedEditorCreatedListenerHandler : NotebookEditorCreatedListener {
        override fun editorCreated(
            editor: Editor,
            virtualFile: BackedNotebookVirtualFile
        ) {
            Disposer.dispose(eventsTransformer)
            eventsTransformer = createDisposableChild {
                DocumentInputEventsTransformer(editor, document, highlightingEventsQueue)
            }
            passService.markUpErrorsTracker.addMarkupListener(editor)
        }
    }

    private val document by lazy {
        withReadAccess {
            FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
        }
    }

    private val iterationLock = Mutex(false)
    // todo: fields can be lazily initialized?
    private val iterationStateIndicator = DaemonIterationState()
    // todo: this is old, to remove
    private val highlightingPassStateTracker = createDisposableChild {
        HighlightingPassStateTracker(project)
    }

    // NEW STUFF
    private val highlightingEventsQueue = createDisposableChild {
        HighlightingEventsQueueImpl(project, virtualFile)
    }
    private val passService = createDisposableChild {
        HighlightingPassServiceImpl(
            highlightingEventsQueue,
            virtualFile.file.name
        )
    }
    // todo: add execution helper controller
    private lateinit var eventsTransformer: DocumentInputEventsTransformer

    private var topLevelFile: PsiFile? = null

    val dataController: NotebookPerFileHighlightingMetaDataController = createDisposableChild {
      NotebookPerFileHighlightingMetaDataController(
        project,
        virtualFile,
      )
    }

    private suspend fun updateData(editorCells: EditorCells) {
        iterationLock.withLock {
            highlightingEventsQueue.pushEvent(
                HighlightingEvent(
                    focusCell = editorCells.focusCell,
                    null,
                    changedCells = editorCells.allCellOrdinals,
                )
            )
        }
    }

    @RequiresReadLock
    private fun getEditorCellsIndexes(): EditorCells? {
        ThreadingAssertions.assertReadAccess()
        val currentVFile = virtualFile.file
        val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(currentVFile) as? TextEditor ?: return null
        val editor = textEditor.editor
        val cells = getAllIntervalPointers(editor).mapNotNull { it.get()?.ordinal }
        val focusCell = editor.getCellByOffset(editor.caretModel.offset).ordinal
        return EditorCells(focusCell, cells)
    }

    private fun Disposable.addListeners() {
        //document.addDocumentListener(
        //    ImpatientNotebookChangeListener(project, virtualFile),
        //    this
        //)
        project.messageBus.connect(this).subscribe(
            NotebookEditorCreatedListener.TOPIC, EditorCreatedEditorCreatedListenerHandler()
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
        val editorCells = readAction {
            getEditorCellsIndexes()
        }
        if (editorCells == null) {
            LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
        } else {
            updateData(editorCells)
        }

        readAction {
            topLevelFile = virtualFile.file.findPsiFile(project)
            addListeners()
        }
    }

    fun isFileTarget(file: KtFile): Boolean {
        return passService.shouldHighlightErrorsInFile(file)
    }

    fun getRangesToHighlight(file: PsiFile, editor: Editor, cells: List<PsiLanguageInjectionHost>?): Collection<TextRange> {
        if (cells == null) {
            LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
        }

        return passService.getRangesToHighlight(file, editor)
    }

    fun finishedAnalysisForFile(psiFile: PsiFile): Deferred<Unit> = coroutineScope.async {
        // todo: we don't need it?
    }

    fun daemonFinished(editor: Editor, psiFile: PsiFile) {
        if (passService.passState.isIdle) return

        coroutineScope.async {
            if (passService.passState.isIdle) {
                return@async
            }

            iterationLock.withLock {
                passService.passFinished(editor, psiFile)
            }
        }
    }

    fun restartAnalysing() {
        coroutineScope.async {
            runCatching {
                val editorCells = readAction {
                    getEditorCellsIndexes()
                }
                if (editorCells == null) {
                    LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
                    return@async
                }
                updateData(editorCells)

                NotebookHighlightingRestarter.scheduleRegularUpdate(topLevelFile!!)
            }.onFailure {
                LOG.warn("Problem during restarting analysis for $virtualFile: ", it)
            }
        }
    }

    override fun dispose() {
        clearState()
    }

    private fun clearState() {
        passService.markUpErrorsTracker.resetState(completeRangeInd, completeReset = true)
        topLevelFile = null
    }
}
