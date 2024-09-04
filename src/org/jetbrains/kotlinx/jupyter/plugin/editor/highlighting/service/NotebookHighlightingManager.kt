// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.TreeTraversal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.ImpatientNotebookChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookPerFileChildService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.core.impl.file.doubleFile.NotebookVirtualFileSystem
import com.intellij.jupyter.core.editor.getAllIntervalPointers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

class NotebookHighlightingManager(
  virtualFile: BackedNotebookVirtualFile,
  private val document: Document,
  projectService: NotebookHighlightingService,
  childScope: CoroutineScope,
  var completeRangeInd: Int?
): NotebookPerFileChildService(virtualFile, childScope) {
    companion object {
        private val LOG = thisLogger()

        data class InjectedFileData(
            val notebookCellIndex: Int,
            val file: KtFile,
            val ktFileRange: TextRange,
            val injectionHost: PsiLanguageInjectionHost,
            val totalTokens: Int
        ) {
            val processedTokens = AtomicInteger(0)
        }

        enum class DaemonState {
            IDLE,
            SETUP,
            IN_PROGRESS,
        }

        private const val INJECTED_SYNTAX_LAYER_BORDER = HighlighterLayer.CARET_ROW - 1
    }
    private val project: Project = projectService.project
    private val iterationLock = ReentrantReadWriteLock()
    private val iterationState = AtomicReference(DaemonState.IDLE)

    val dataController = NotebookPerFileHighlightingMetaDataController(
        virtualFile,
        NotebookCellExecutionHighlightingHelper(project, virtualFile),
        this
    )
    private var _jupyterFile: PsiFile? = null
    val jupyterPsiFile: PsiFile? get() = _jupyterFile

    private fun initializeData(restart: Boolean = false) {
        iterationLock.withWriteLock {
            val targetData = mutableSetOf<Int>()
            // if NotebookVirtualFileSystem is not ready yet
            val currentVFile = if (virtualFile !is BackedNotebookVirtualFile) {
                NotebookVirtualFileSystem.getNotebookVirtualFileSystem().refreshAndFindFileByPath(virtualFile.originFile.path)!!
            } else virtualFile.file

            val editorState = (FileEditorManager.getInstance(project).getSelectedEditor(currentVFile) as? TextEditor)?.editor
            val cells = if (editorState != null) {
                getAllIntervalPointers(editorState).mapNotNull { it.get()?.ordinal }
            } else virtualFile.file.toPsiFile(project).getNotebookCells().indices

            targetData.addAll(cells)

            dataController.update {
                notebookRangesQueuedForHL = targetData
                notebookDocumentStructureNontrivialChanged.set(false)
            }
        }

        if (virtualFile.file.isKotlinNotebook && !restart) {
            _jupyterFile = virtualFile.file.toPsiFile(project)
            document.addDocumentListener(
                ImpatientNotebookChangeListener(project, virtualFile),
                this
            )
        }
    }

    private fun Disposable.addListener() {
        val targetFile = virtualFile
        project.messageBus.connect(this).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun sessionStarted(virtualFile: BackedNotebookVirtualFile, isAfterRestart: Boolean) {
                    if (targetFile != virtualFile || !isAfterRestart) return

                    coroutineScope.launch {
                        readAction {
                            restartAnalysing()
                        }
                    }
                }
            }
        )
    }

    private fun addNewMarkupListener(editor: Editor) {
        activeMarkupModelListener = object : MarkupModelListener {
            override fun afterAdded(highlighter: RangeHighlighterEx) {
                val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return
                // ignore parsing errors for now, only from KT factories
                if (info.severity == HighlightSeverity.ERROR && info.description.startsWith('[')) {
                    targetErrorHighlighters.add(highlighter)
                }
            }
        }
        (editor as? EditorEx)
            ?.filteredDocumentMarkupModel?.addMarkupModelListener((editor as? EditorImpl)?.disposable ?: this, activeMarkupModelListener)
    }

    init {
        Disposer.register(projectService, this)
        initializeData()
        projectService.addListener()
    }

    private val fileToInjectionData = ConcurrentHashMap<KtFile, InjectedFileData>()

    private var targetPsiFile: PsiFile? = null
    private var activeCaretListener: NotebookCaretListener? = null
    private lateinit var activeMarkupModelListener: MarkupModelListener
    private val finishedFiles = mutableSetOf<Int>()
    private val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()
    private val knownErrorInd = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    private val targetIndexes: Set<Int>
        get() = iterationLock.withReadLock { fileToInjectionData.mapTo(mutableSetOf()) { it.value.notebookCellIndex } }
    private val finishedHighlighting: Set<Int>
        get() = try {
            finishedFiles - (knownErrorInd.keys - (completeRangeInd ?: -1))
        } catch (ex: Exception) {
            completeRangeInd?.let { setOf(it) } ?: emptySet()
        }

    private val remainingIndexesToProcess: Set<Int>
        get() = targetIndexes - finishedHighlighting

    private val unrecognizedFiles: ConcurrentLinkedDeque<PsiFile> = ConcurrentLinkedDeque()

    private fun isCanModifyHLRequests(project: Project): Boolean =
        !JupyterKtScriptingSupport.isInTheTransaction(project)

    fun tryGetKnownHostFor(file: PsiFile): PsiLanguageInjectionHost? {
        if (file !is KtFile) return null
        return fileToInjectionData.getOrElse(file, defaultValue = { null })?.injectionHost
    }

    fun isFileTarget(file: PsiFile): Boolean {
        return file == targetPsiFile
    }

    fun associateWithNewCaretListener(listener: NotebookCaretListener, editor: Editor) {
        activeCaretListener = listener
        addNewMarkupListener(editor)
    }

    private fun enterSetupPhase(): Boolean {
        return iterationState.compareAndSet(DaemonState.IDLE, DaemonState.SETUP)
    }

    fun passCreated(project: Project, targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        if (cells == null) {
            LOG.warn("Cells are null, nothing can be done")
        }

        if (!enterSetupPhase()) {
            LOG.info("Another pass is in setup, aborting")
            return
        }

        clearState()
        val manager = InjectedLanguageManager.getInstance(project)
        targetIndexes.forEach { ind ->
            cells?.getOrNull(ind)?.let {
                manager.getInjectedPsiFiles(it)?.let { injected ->
                    // skip non Kt
                    if (injected.none { f -> f.first is KtFile }) {
                        finishedFiles.add(ind)
                        return@forEach
                    }
                    injected.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                        val ktFileRange = manager.injectedToHost(ktFile, ktFile.textRange)
                        fileToInjectionData[ktFile as KtFile] = InjectedFileData(
                            ind,
                            ktFile,
                            ktFileRange,
                            it,
                            numberOfNonWhiteSpaceLeaves(ktFile)
                        )
                        if (ind == completeRangeInd) targetPsiFile = ktFile
                    }
                } ?: finishedFiles.add(ind)
            }
        }
        iterationState.compareAndSet(DaemonState.SETUP, DaemonState.IN_PROGRESS)
        unrecognizedFiles.clear()
        this.completeRangeInd = completeRangeInd
    }

    private fun numberOfNonWhiteSpaceLeaves(ktFile: KtFile): Int {
        return SyntaxTraverser.psiTraverser(ktFile)
            .traverse(TreeTraversal.LEAVES_DFS)
            .count { psiLeaf ->
                PsiUtilCore.getElementType(psiLeaf) != TokenType.WHITE_SPACE
                        && psiLeaf !is KtPackageDirective
            }
    }

    private fun clearState(complete: Boolean = false) {
        targetPsiFile = null
        fileToInjectionData.clear()
        finishedFiles.clear()
        activeCaretListener = null
        if (complete) {
            targetErrorHighlighters.clear()
            knownErrorInd.clear()
            targetPsiFile = null
            _jupyterFile = null
        } else {
            completeRangeInd?.let {
                knownErrorInd[it]?.addAll(targetErrorHighlighters)
            }
            targetErrorHighlighters.clear()
        }
    }

    private fun Collection<PsiFile>.toCellsIndexes(manager: InjectedLanguageManager): List<Int> {
        val cells = jupyterPsiFile.getNotebookCells()
        return mapNotNull { injected -> cells.indexOf(manager.getInjectionHost(injected)) }
    }

    fun resetCaretListenerState() {
        activeCaretListener?.resetState()
    }

    fun finishedAnalysisForFile(psiFile: PsiFile, holder: HighlightInfoHolder) = coroutineScope.async {
        val ind = fileToInjectionData[psiFile]?.notebookCellIndex
        if (ind == null) {
            unrecognizedFiles.add(psiFile)
            LOG.info("Seen unrecognized file, will redo")
            return@async
        }

        if (!isCanModifyHLRequests(psiFile.project)) {
            LOG.info("Not allowed to change $ind, will redo")
            return@async
        }
        iterationLock.withWriteLock {
            finishedFiles.addIfNotNull(ind)
            LOG.info("Finished for $ind")
        }
        if (psiFile != targetPsiFile || !holder.hasErrorResults()) {
            withContext(Dispatchers.EDT) {
                knownErrorInd[ind]?.forEach { oldError ->
                    oldError.dispose()
                }
            }
            return@async
        }
        knownErrorInd.putIfAbsent(ind, mutableSetOf())
    }

    private fun determineFilesWithLeftErrors(markupModel: MarkupModelEx, completeIndexTarget: Int?) {
        val keys = knownErrorInd.filterKeys { it != completeIndexTarget }
        val toRemove = mutableSetOf<Int>()
        keys.forEach { entry ->
            val data = knownErrorInd[entry.key]
            data?.removeIf {
                it.layer == -1 || !it.isValid || !markupModel.containsHighlighter(it)
            }
            if (data?.isEmpty() == true) toRemove.add(entry.key)
        }

        completeIndexTarget?.let {
            knownErrorInd[it]?.addAll(targetErrorHighlighters)
        }
        toRemove.forEach { knownErrorInd.remove(it) }
        val targetPassed = completeIndexTarget in finishedFiles

        knownErrorInd.filter {
            if (it.key != completeIndexTarget) it.value.isNotEmpty() else !targetPassed
        }.keys.also {
            // not yet counted
            if (it.isNotEmpty()) {
                finishedFiles.removeAll(it)
                LOG.debug("Daemon finished, knownErrorInd: ${knownErrorInd.keys}, recycled errors in ind: $toRemove, remaining: ${it}")
            }
        }
    }

    private fun determineHighlightedFiles(markupModel: MarkupModelEx) {
        fileToInjectionData.forEach { (ktFile, data) ->
            val range = data.ktFileRange
            if (ktFile.text.isBlank()) return@forEach
            data.processedTokens.set(0)
            val seenHighlighters = mutableSetOf<RangeHighlighter>()

            markupModel.processRangeHighlightersOverlappingWith(range.startOffset, range.endOffset) {
                // injected syntax is greater than regular SYNTAX
                if ((it.layer < INJECTED_SYNTAX_LAYER_BORDER && it.layer != HighlighterLayer.ERROR) && seenHighlighters.add(it)) {
                    data.processedTokens.incrementAndGet()
                }
                true
            }
            val token = data.processedTokens.get().floorDiv(2)

            if (token < data.totalTokens - 1) {
                finishedFiles.remove(data.notebookCellIndex)
            }
        }
    }

    // returns true if all updates are processed
    fun daemonFinished(editor: Editor, psiFile: PsiFile?) {
        val markup = (editor as? EditorEx)?.filteredDocumentMarkupModel ?: return
        if (iterationState.get() == DaemonState.IDLE) return

        coroutineScope.launch {
            if (iterationState.get() == DaemonState.IDLE) return@launch

            processDaemonFinished(editor, psiFile, markup)
        }
    }

    fun editorPotentiallyDisposed() {
        clearState()
    }

    fun restartAnalysing() {
        initializeData(true)
        NotebookHighlightingRestarter.scheduleRegularUpdate(jupyterPsiFile!!)
    }

    override fun dispose() {
        clearState(true)
    }

    @RequiresBackgroundThread
    private suspend fun processDaemonFinished(editor: Editor, psiFile: PsiFile?, markup: MarkupModelEx) {
        val queue = dataController.notebookRangesQueuedForHL
        val canModifyRequests = isCanModifyHLRequests(project)
        val target = completeRangeInd

        iterationLock.withWriteLock {
            if (iterationState.get() == DaemonState.IDLE) {
                return@withWriteLock
            }
            reduceQueue(canModifyRequests)
            queue?.addIfNotNull(target)
            iterationState.set(DaemonState.IDLE)

            val completedIndexes = finishedHighlighting
            val executionRequestsDone = dataController
                .executionHighlightingHelper
                .daemonFinished(completedIndexes, queue, canModifyRequests)
            val remaining = remainingIndexesToProcess

            determineFilesWithLeftErrors(markup, completeRangeInd)
            val manager = InjectedLanguageManager.getInstance(project)
            val seenNewFiles = unrecognizedFiles.isNotEmpty()

            readAction {
                determineHighlightedFiles(markup)

                if (seenNewFiles) {
                    queue?.addAll(unrecognizedFiles.toCellsIndexes(manager))
                    unrecognizedFiles.clear()
                }
            }

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
            LOG.info("Reducing queue by $completedIndexes, left: $remaining, canModify: ${canModifyRequests}, exec requests done: $executionRequestsDone")
        }
    }

    private fun reduceQueue(canModifyRequests: Boolean) {
        val queue = dataController.notebookRangesQueuedForHL
        val finished = finishedHighlighting
        // we don't want to lose any updates happened during concurrent modification or delay
        if (queue != null && finished.isNotEmpty() && canModifyRequests) {
            queue.removeAll(finished)
        }
    }
}