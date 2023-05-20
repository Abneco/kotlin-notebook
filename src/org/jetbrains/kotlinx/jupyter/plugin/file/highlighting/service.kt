// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingRestarter.UpdateSteps.performHLStartupTemplate
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentStructureNontrivialChanged
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.ImpatientNotebookChangeListener
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class NotebookHighlightingService(val project: Project): Disposable {
    private val mapping: MutableMap<VirtualFile, NotebookHighlightingManager> = ConcurrentHashMap()

    fun getOrCreate(virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
        return mapping.getOrPut(virtualFile.file) { NotebookHighlightingManager(virtualFile, this, null) }
    }

    override fun dispose() {
        mapping.clear()
    }

    companion object {
        const val HL_DELAY_PAUSE: Long = 300
        fun getInstance(project: Project) = project.service<NotebookHighlightingService>()
        // maybe for the doc
        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}


class NotebookHighlightingManager(
    val virtualFile: BackedNotebookVirtualFile,
    projectService: NotebookHighlightingService,
    var completeRangeInd: Int?
): Disposable {
    companion object {
        private val LOG = thisLogger()
    }

    val document = FileDocumentManager.getInstance().getDocument(virtualFile.file)!!

    private fun initialiseData(project: Project) {
        val targetData = mutableSetOf<Int>()
        targetData.addAll(
            virtualFile.file.toPsiFile(project)?.getNotebookCellList()?.indices?.toList() ?: emptyList()
        )
        document.putUserData(NotebookHighlightingUtilityObject.NotebookQueuedTargetRanges, targetData)
        document.putUserData(NotebookDocumentStructureNontrivialChanged, AtomicReference(false))
        if (virtualFile.file.isKotlinNotebook) {
            document.addDocumentListener(
                ImpatientNotebookChangeListener(project, virtualFile),
                this
            )
        }
    }

    private fun addNewMarkupListener(editor: Editor) {
        activeMarkupModelListener = object : MarkupModelListener {
            override fun afterAdded(highlighter: RangeHighlighterEx) {
                val info = highlighter.errorStripeTooltip as? HighlightInfo ?: return
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
        initialiseData(projectService.project)
    }

    private val fileToInjectionData: MutableMap<KtFile, Pair<PsiLanguageInjectionHost, Int>> = mutableMapOf()
    private var targetPsiFile: PsiFile? = null

    private lateinit var activeCaretListener: NotebookCaretListener
    private lateinit var activeMarkupModelListener: MarkupModelListener
    val caretListener: NotebookCaretListener get() = activeCaretListener

    private val finishedFiles = mutableSetOf<Int>()
    private val targetErrorHighlighters = ConcurrentCollectionFactory.createConcurrentSet<RangeHighlighter>()
    private val knownErrorInd = ConcurrentHashMap<Int, MutableSet<RangeHighlighter>>()
    private val targetIndexes: Set<Int>
        get() = fileToInjectionData.values.mapTo(mutableSetOf()) { it.second }
    val finishedHighlighting: Set<Int>
        get() = finishedFiles - (knownErrorInd.keys - (completeRangeInd ?: -1))

    val remainingIndexesToProcess: Set<Int>
        get() = targetIndexes - finishedHighlighting

    fun tryGetKnownHostFor(file: PsiFile): PsiLanguageInjectionHost? {
        if (file !is KtFile) return null
        return fileToInjectionData.getOrElse(file, defaultValue = { null })?.first
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
                        fileToInjectionData[ktFile as KtFile] = it to ind
                        if (ind == completeRangeInd) targetPsiFile = ktFile
                    }
                } ?: finishedFiles.add(ind)
            }
        }
        this.completeRangeInd = completeRangeInd
    }

    private fun clearState(complete: Boolean = false) {
        targetPsiFile = null
        fileToInjectionData.clear()
        finishedFiles.clear()
        if (complete) {
            targetErrorHighlighters.clear()
            knownErrorInd.clear()
            targetPsiFile = null
        } else {
            completeRangeInd?.let {
                knownErrorInd[it]?.addAll(targetErrorHighlighters)
            }
            targetErrorHighlighters.clear()
        }
    }

    fun finishedAnalysisForFile(psiFile: PsiFile, holder: HighlightInfoHolder) {
        val ind = fileToInjectionData[psiFile]?.second
        finishedFiles.addIfNotNull(ind)
        if (psiFile != targetPsiFile || !holder.hasErrorResults()) {
            invokeLater {
                ind?.let {
                    knownErrorInd[it]?.forEach { oldError ->
                        oldError.dispose()
                    }
                }
            }
            return
        }
        ind?.let {
            knownErrorInd.putIfAbsent(it, mutableSetOf())
        }
    }

    // returns true if all updates are processed
    fun daemonFinished(editor: Editor, psiFile: PsiFile?, queue: MutableSet<Int>?, executionRequestsDone: Boolean): Boolean {
        val markup = (editor as? EditorEx)?.filteredDocumentMarkupModel ?: return true
        val completeInd = completeRangeInd
        val keys = knownErrorInd.filterKeys { it != completeInd }
        val toRemove = mutableSetOf<Int>()
        keys.forEach { entry ->
            val data = knownErrorInd[entry.key]
            data?.removeIf {
                it.layer == -1 || !it.isValid || !markup.containsHighlighter(it)
            }
            if (data?.isEmpty() == true) toRemove.add(entry.key)
        }

        completeInd?.let {
            knownErrorInd[it]?.addAll(targetErrorHighlighters)
        }
        toRemove.forEach { knownErrorInd.remove(it) }
        val targetPassed = completeInd in finishedFiles

        knownErrorInd.filter {
                if (it.key != completeInd) it.value.isNotEmpty() else !targetPassed
        }.keys.also { // not yet counted
           finishedFiles.removeAll(it)
           LOG.debug("Daemon finished, knownErrorInd: ${knownErrorInd.keys}, recycled errors in ind: $toRemove, remaining: ${it}")
        }

        val remaining = remainingIndexesToProcess
        val isLeft = remaining.isNotEmpty()

        if (isLeft || !executionRequestsDone) {
            psiFile?.let {
                NotebookHighlightingRestarter.scheduleRegularUpdate(document, psiFile)
            }
        }
        if (isLeft) {
            queue?.addAll(remaining)
        }

        return !isLeft
    }

    fun editorPotentiallyDisposed() {
        clearState()
    }

    override fun dispose() {
        clearState(true)
    }
}


internal object NotebookHighlightingRestarter {
    private var updateJob: Job? = null
    private val regularUpdateScope = CoroutineScope(Dispatchers.Default)

    object UpdateSteps {
        internal val postScriptingUpdateStep: (Document?) -> Unit = {
            it?.getUserData(NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange)?.compareAndSet(false, true)
        }

        suspend inline fun performHLStartupTemplate(
            file: PsiFile, delayDelta: Long,
            crossinline afterRequest: () -> Unit = {}
        ) {
            val analyzer = DaemonCodeAnalyzer.getInstance(file.project) as DaemonCodeAnalyzerImpl
            delay(delayDelta)
            while (analyzer.isRunning) {
                delay(150)
            }
            readAction {
                file.restartAnalyzing()
            }
            afterRequest()
        }
    }

    fun scheduleRegularUpdateNoChecks(file: PsiFile, delayDelta: Long = HL_DELAY_PAUSE) {
        regularUpdateScope.launch {
            delay(delayDelta)
            readAction { file.restartAnalyzing() }
        }
    }

    inline fun scheduleRegularUpdate(document: Document?, file: PsiFile, delayDelta: Long = HL_DELAY_PAUSE,
                                     crossinline afterRequest: () -> Unit = {},
                                     crossinline undoRequest: () -> Unit = {}) {
        if (!shouldStartAfterPreChecks(file, updateJob, afterRequest, undoRequest)) return
        updateJob = regularUpdateScope.launch {
            performHLStartupTemplate(file, delayDelta, afterRequest)
        }
    }
}
