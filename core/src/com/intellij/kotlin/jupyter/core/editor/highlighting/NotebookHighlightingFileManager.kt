// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.getAllIntervalPointers
import com.intellij.jupyter.execution.listeners.NotebookSessionEventListener
import com.intellij.kotlin.jupyter.core.editor.hack.pass.HighlightingPassServiceImpl
import com.intellij.kotlin.jupyter.core.editor.hack.pass.NotebookCellFocusInformation
import com.intellij.kotlin.jupyter.core.editor.hack.queue.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.hack.queue.HighlightingEventsQueueImpl
import com.intellij.kotlin.jupyter.core.editor.hack.restarter.NotebookAnalysisRestarter
import com.intellij.kotlin.jupyter.core.editor.highlighting.document.DocumentInputEventsTransformer
import com.intellij.kotlin.jupyter.core.editor.highlighting.editor.NotebookEditorCreatedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.pass.NotebookPassConfiguration
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
                            DocumentInputEventsTransformer(editor, document, highlightingEventsQueue)
                        }
                        passService.markUpErrorsTracker.addMarkupListener(editor)
                    }

                    restartAnalysing()
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
    // todo: fields can be lazily initialized?

    // NEW
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

    // todo: add execution helper controller
    private lateinit var eventsTransformer: DocumentInputEventsTransformer

    private var topLevelFile: PsiFile? = null

    val focusInformation: NotebookCellFocusInformation?
        get() {
            val passConfiguration = passService.currentPassConfiguration
            if (passConfiguration == NotebookPassConfiguration.EMPTY) return null
            val focusCellData = passConfiguration.filesToHL[passConfiguration.targetKtFile] ?: return null

            return NotebookCellFocusInformation(focusCellData.notebookCellIndex, focusCellData.injectionHost.textRange)
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
    private fun getEditorCellsIndexes(): EditorCells? {
        ThreadingAssertions.assertReadAccess()
        val currentVFile = virtualFile.file
        val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(currentVFile) as? TextEditor ?: return null
        val editor = textEditor.editor
        val cells = getAllIntervalPointers(editor).mapNotNull { it.get()?.ordinal }
        val focusCell = editor.getCellByOffset(editor.caretModel.offset).ordinal
        return EditorCells(focusCell, cells)
    }

    private fun addListeners() {
        addNotebookSessionEventListener()
        addNotebookScriptsStateListener()
        addCodeAnalyzerListener()
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
        addEditorUpdateListener()
        initialiseComponents()
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

                NotebookAnalysisRestarter.scheduleRegularUpdate(topLevelFile!!)
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
