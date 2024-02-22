// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase.TokenInfo
import com.intellij.refactoring.suggested.startOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlinx.jupyter.plugin.debug.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingRestarter.UpdateSteps.performHLStartupTemplate
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution.KotlinNotebookCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.ImpatientNotebookChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadAccess
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class NotebookHighlightingService(
    val project: Project, coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookHighlightingManager>(coroutineScope) {

    override fun createInstance(virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
        return withReadAccess {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
            NotebookHighlightingManager(virtualFile, document, this@NotebookHighlightingService, null)
        }
    }

    companion object {
        const val HL_DELAY_PAUSE: Long = 300
        fun getInstance(project: Project) = project.service<NotebookHighlightingService>()
        // maybe for the doc
        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
            return getInstance(project).getOrCreate(virtualFile)
        }

        fun VirtualFile?.getHighlightingManagerForFile(project: Project) =
            this?.let(BackedNotebookVirtualFile::takeIfBacked)?.let { getForFile(project, it) }
    }
}


class NotebookHighlightingManager(
    val virtualFile: BackedNotebookVirtualFile,
    private val document: Document,
    projectService: NotebookHighlightingService,
    var completeRangeInd: Int?
): Disposable {
    companion object {
        private val LOG = thisLogger()

        data class InjectedFileData(
            val notebookCellIndex: Int,
            val file: KtFile,
            val ktFileRange: TextRange,
            val injectionHost: PsiLanguageInjectionHost,
            val injectionHostOffset: Int,
            val injectedTokens: Collection<TokenInfo>
        ) {
            val totalTokens = injectedTokens.size
            val processedTokens = AtomicInteger(0)
        }
    }
    private val project: Project = projectService.project

    val dataController = NotebookPerFileHighlightingMetaDataController(
        virtualFile,
        NotebookCellExecutionHighlightingHelper(project, virtualFile),
        this
    )
    private var _jupyterFile: PsiFile? = null
    val jupyterPsiFile: PsiFile? get() = _jupyterFile

    private fun initializeData(restart: Boolean = false) {
        val targetData = mutableSetOf<Int>()
        targetData.addAll(
            virtualFile.file.toPsiFile(project).getNotebookCells().indices
        )
        dataController.update {
            notebookRangesQueuedForHL = targetData
            notebookDocumentStructureNontrivialChanged.set(false)
        }
        if (virtualFile.file.isKotlinNotebook && !restart) {
            _jupyterFile = virtualFile.file.toPsiFile(project)
            document.addDocumentListener(
                ImpatientNotebookChangeListener(project, virtualFile),
                this
            )
        }
    }

    private fun Disposable.addListeners() {
        project.messageBus.connect(this).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun sessionRestarted(virtualFile: BackedNotebookVirtualFile) {
                    KotlinNotebookCellExecutionCallbackFactory.getInstance().sessionRestarted(virtualFile)
                    dataController.executionHighlightingHelper.onSessionRestarted()
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
        projectService.addListeners()
    }

    private val fileToInjectionData = ConcurrentHashMap<KtFile, InjectedFileData>()

    private var targetPsiFile: PsiFile? = null
    private var activeCaretListener: NotebookCaretListener? = null
    private lateinit var activeMarkupModelListener: MarkupModelListener
    private val requestWasCompleted = AtomicBoolean(false)
    private val finishedFiles = mutableSetOf<Int>()
    private val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()
    private val knownErrorInd = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    private val targetIndexes: Set<Int>
        get() = fileToInjectionData.mapTo(mutableSetOf()) { it.value.notebookCellIndex }
    val finishedHighlighting: Set<Int>
        get() = try {
            finishedFiles - (knownErrorInd.keys - (completeRangeInd ?: -1))
        } catch (ex: Exception) { completeRangeInd?.let { setOf(it) } ?: emptySet() }

    private val remainingIndexesToProcess: Set<Int>
        get() = targetIndexes - finishedHighlighting

    private val unrecognizedFiles: ConcurrentLinkedDeque<PsiFile> = ConcurrentLinkedDeque()

    fun isCanModifyHLRequests(project: Project): Boolean =
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

    fun passCreated(project: Project, targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        if (cells == null) {
            LOG.warn("Cells are null, nothing can be done")
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
                            it.startOffset,
                            InjectedLanguageUtilBase.getHighlightTokens(ktFile).filter { token ->
                                token.type != WHITE_SPACE
                            }
                        )
                        if (ind == completeRangeInd) targetPsiFile = ktFile
                    }
                } ?: finishedFiles.add(ind)
            }
        }
        targetIndexes.ifNotEmpty { requestWasCompleted.set(false) }
        unrecognizedFiles.clear()
        this.completeRangeInd = completeRangeInd
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

    fun finishedAnalysisForFile(psiFile: PsiFile, holder: HighlightInfoHolder) {
        val ind = fileToInjectionData[psiFile]?.notebookCellIndex
        if (ind == null) {
            unrecognizedFiles.add(psiFile)
            //finishedFiles.clear()
            //dataController.notebookRangesQueuedForHL?.addAll(targetIndexes)
            LOG.debug("Seen unrecognized file, will redo")
            return
        }

        if (!isCanModifyHLRequests(psiFile.project)) {
            LOG.debug("Not allowed to change $ind, will redo")
            return
        }
        finishedFiles.addIfNotNull(ind)
        if (psiFile != targetPsiFile || !holder.hasErrorResults()) {
            runInEdt {
                knownErrorInd[ind]?.forEach { oldError ->
                    oldError.dispose()
                }
            }
            return
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
                if (it.layer == 1998 && (it.errorStripeTooltip as? HighlightInfo)?.text?.isNotBlank() == true && seenHighlighters.add(it)) {
                    data.processedTokens.incrementAndGet()
                }
                true
            }

            if (data.processedTokens.get() / 2 < data.totalTokens) {
                finishedFiles.remove(data.notebookCellIndex)
            }
        }
    }

    // returns true if all updates are processed
    fun daemonFinished(editor: Editor, psiFile: PsiFile?, queue: MutableSet<Int>?, canModifyRequests: Boolean): Boolean {
        val markup = (editor as? EditorEx)?.filteredDocumentMarkupModel ?: return true
        val completedIndexes = finishedHighlighting
        val executionRequestsDone = dataController
            .executionHighlightingHelper
            .daemonFinished(completedIndexes, queue, canModifyRequests)

        determineFilesWithLeftErrors(markup, completeRangeInd)
        determineHighlightedFiles(markup)

        val project = editor.project
        if (project == null) {
            LOG.warn("Project is null for editor $editor in file: $psiFile")
            return true
        }

        val manager = InjectedLanguageManager.getInstance(project)
        val seenNewFiles = unrecognizedFiles.isNotEmpty()
        if (seenNewFiles) {
            queue?.addAll(unrecognizedFiles.toCellsIndexes(manager))
        }
        val remaining = remainingIndexesToProcess
        if (remaining.isEmpty()) {
            requestWasCompleted.set(true)
        }
        val isLeft = remaining.isNotEmpty() || seenNewFiles

        if (isLeft || !executionRequestsDone) {
            psiFile?.let {
                NotebookHighlightingRestarter.scheduleRegularUpdate(psiFile)
            }
        }
        if (isLeft) {
            queue?.addAll(remaining)
        }
        LOG.debug("Reducing queue by $completedIndexes, canModify: ${canModifyRequests}, exec requests done: $executionRequestsDone")

        return !isLeft
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
}


internal object NotebookHighlightingRestarter {
    private var updateJob: Job? = null
    private val regularUpdateScope = CoroutineScope(Dispatchers.Default)

    object UpdateSteps {

        suspend inline fun performHLStartupTemplate(
            file: PsiFile, delayDelta: Long,
            crossinline afterRequest: () -> Unit = {}
        ) {
            val analyzer = DaemonCodeAnalyzerStatusService.getInstance(file.project)
            delay(delayDelta)
            while (analyzer.daemonRunning) {
                delay(150)
            }
            readAction {
                file.restartAnalyzing()
            }
            afterRequest()
        }
    }

    fun scheduleRegularUpdateNoChecks(
        file: PsiFile,
        delayDelta: Long = HL_DELAY_PAUSE
    ) {
        regularUpdateScope.launch {
            delay(delayDelta)
            readAction { file.restartAnalyzing() }
        }
    }

    inline fun scheduleRegularUpdate(
        file: PsiFile,
        delayDelta: Long = HL_DELAY_PAUSE,
        crossinline afterRequest: () -> Unit = {},
        crossinline undoRequest: () -> Unit = {}
    ) {
        if (!shouldStartAfterPreChecks(file, updateJob, afterRequest, undoRequest)) return
        updateJob = regularUpdateScope.launch {
            performHLStartupTemplate(file, delayDelta, afterRequest)
        }
    }
}
