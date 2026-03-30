// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.getAllIntervalPointers
import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.DocumentInputEventsTransformerAdapter
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.HighlightingPassServiceImpl
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.NotebookCellFocusInformation
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.NotebookPassConfiguration
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEventsQueueImpl
import com.intellij.kotlin.jupyter.core.editor.highlighting.editor.NotebookEditorCreatedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.restarter.NotebookHighlightingRestarter
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.createDisposableChild
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.isCurrentlySelectedInEditor
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.notebooks.visualization.getCellByOffset
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
): NotebookPerFileChildService(virtualFile, childScope) {
    companion object {
        private val LOG = notebookLogger()
    }
    // TODO: should dispatching be moved to the project-level service?
    inner class EditorCreatedListenerHandler : NotebookEditorCreatedListener {
        override fun editorCreated(
            editor: Editor,
            notebookVirtualFile: BackedNotebookVirtualFile
        ) {
            if (virtualFile != notebookVirtualFile) {
                return
            }

            coroutineScope.async {
                editorChangeChannel.send(editor)
            }
        }

        init {
            setupEditorCreationHandler()
        }

        private fun setupEditorCreationHandler() {
            coroutineScope.async {
                for (editor in editorChangeChannel) {
                    iterationLock.withLock {
                        if (::eventsTransformer.isInitialized) {
                            Disposer.dispose(eventsTransformer)
                        }

                        eventsTransformer = createDisposableChild {
                            DocumentInputEventsTransformerAdapter(editor, document, highlightingEventsQueue)
                        }
                        passService.markUpErrorsTracker.addMarkupListener(editor)
                    }

                    queueEditorCellsToHighlight(editor)
                }
            }
        }
    }

    /**
     * Means we receive only the last event.
     */
    private val editorChangeChannel = Channel<Editor>(Channel.CONFLATED)

    private val document by lazy {
        withReadAccess {
            FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
        }
    }

    private val iterationLock = Mutex(false)

    private val highlightingEventsQueue = createDisposableChild {
        HighlightingEventsQueueImpl(project, virtualFile)
    }
    private val passService = createDisposableChild {
        HighlightingPassServiceImpl(
            project,
            highlightingEventsQueue,
            virtualFile.file.name
        )
    }

    private lateinit var eventsTransformer: DocumentInputEventsTransformerAdapter

    private var topLevelFile: PsiFile? = null

    val focusInformation: NotebookCellFocusInformation?
        get() {
            val passConfiguration = passService.currentPassConfiguration
            if (passConfiguration == NotebookPassConfiguration.EMPTY) return null
            val focusCell = passConfiguration.focusCell
            val injectionHost = passConfiguration.editorCells.getOrNull(focusCell) ?: return null

            return NotebookCellFocusInformation(focusCell, injectionHost.textRange)
        }

    private suspend fun updateData(editorCells: EditorCells) {
        iterationLock.withLock {
            highlightingEventsQueue.pushEvent(
                HighlightingEvent(
                    focusCell = editorCells.focusCell,
                    null,
                    changedCells = editorCells.allCellOrdinals,
                    isCustomEditorEvent = true
                )
            )
        }
    }

    @RequiresReadLock
    private fun getEditorCellsIndexes(editor: Editor? = null): EditorCells? {
        ThreadingAssertions.assertReadAccess()
        val fileEditor = when {
            editor == null -> {
                val currentVFile = virtualFile.file
                val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(currentVFile) as? TextEditor ?: return null
                textEditor.editor
            }
            else -> editor
        }
        val cells = getAllIntervalPointers(fileEditor).mapNotNull { it.get()?.ordinal }
        val focusCell = fileEditor.getCellByOffset(fileEditor.caretModel.offset).ordinal
        return EditorCells(focusCell, cells)
    }

    private fun addListeners() {
        addNotebookSessionEventListener()
        addNotebookScriptsStateListener()
        addCodeAnalyzerListener()
    }

    private fun addNotebookSessionEventListener() {
        val targetFile = virtualFile
        JupyterExecutionListener.register(this, object : JupyterExecutionListener {
            override suspend fun sessionCreated(session: JupyterNotebookSession) {
                if (targetFile != session.virtualFile)
                    return

                restartAnalysing()
            }
        })
    }

    private fun addNotebookScriptsStateListener() {
        project.messageBus.connect(this).subscribe(
            NotebookScriptsStateListener.TOPIC,
            createAfterUpdateHandler()
        )
    }

    private fun addCodeAnalyzerListener() {
        project.messageBus.connect(this).subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    val targetEditor = fileEditors.firstOrNull { it.file == virtualFile.file } as? TextEditor ?: return
                    highlightingFinished(targetEditor.editor, psiFile = topLevelFile!!)
                }
            }
        )
    }

    private fun addEditorUpdateListener() {
        project.messageBus.connect(this).subscribe(
            NotebookEditorCreatedListener.TOPIC, EditorCreatedListenerHandler()
        )
    }

    private fun createAfterUpdateHandler(): NotebookScriptsStateListener {
        return NotebookScriptsStateListener { file, updateState ->
            if (file != virtualFile) return@NotebookScriptsStateListener
            if (updateState != NotebookScriptsStateListener.UpdateState.COMPLETE) return@NotebookScriptsStateListener

            val isCurrentFileOpened = virtualFile.isCurrentlySelectedInEditor(project)
            if (isCurrentFileOpened) {
                restartAnalysing()
            }
        }
    }

    init {
        addEditorUpdateListener()
        initialiseComponents()
        coroutineScope.async {
            initializeService()
        }
    }

    private suspend fun initializeService() {
        queueEditorCellsToHighlight(cachedEditor = null)

        readAction {
            topLevelFile = virtualFile.file.findPsiFile(project)
            addListeners()
        }
    }

    private suspend fun queueEditorCellsToHighlight(cachedEditor: Editor? = null) {
        val editorCells = readAction {
            getEditorCellsIndexes(cachedEditor)
        }
        if (editorCells == null) {
            LOG.warn(KotlinNotebookBundle.message("kotlin.jupyter.highlighting.service.null.cells.warning"))
        } else {
            updateData(editorCells)
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

    fun restartAnalysing() {
        coroutineScope.async {
            runCatching {
                queueEditorCellsToHighlight(cachedEditor = null)

                NotebookHighlightingRestarter.scheduleRegularUpdate(topLevelFile!!)
            }.onFailure {
                LOG.warn("Problem during restarting analysis for $virtualFile: ", it)
            }
        }
    }

    private fun initialiseComponents() {
        highlightingEventsQueue.initialize()
        passService.initialize()
    }

    private fun highlightingFinished(editor: Editor, psiFile: PsiFile) {
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

    override fun dispose() {
        clearState()
    }

    private fun clearState() {
        passService.markUpErrorsTracker.resetState(null, completeReset = true)
        topLevelFile = null
        editorChangeChannel.cancel()
    }
}
