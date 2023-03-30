// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
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
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingRestarter.UpdateSteps.performHLStartupTemplate
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingRestarter.UpdateSteps.postScriptingUpdateStep
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
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
        const val HL_DELAY_DELTA: Long = HL_DELAY_PAUSE + 50
        fun getInstance(project: Project) = project.service<NotebookHighlightingService>()
        // maybe for the doc
        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}


class NotebookHighlightingManager(
    val virtualFile: BackedNotebookVirtualFile,
    private val projectService: NotebookHighlightingService,
    var completeRangeInd: Int?
): Disposable {
    companion object {
        private val LOG = thisLogger()
        enum class RestartMode {
            AfterScripting,
            Regular
        }
    }
    init {
        Disposer.register(projectService, this)
    }
    private val psiFile = runReadAction {
        virtualFile.file.toPsiFile(projectService.project)
    }
    private val highlightingRestarter = NotebookHighlightingRestarter
    private val iterationLock = ReentrantReadWriteLock()
    private val fileToInjectionData: MutableMap<KtFile, Pair<PsiLanguageInjectionHost, Int>> = mutableMapOf()
    private var targetPsiFile: PsiFile? = null

    private lateinit var activeCaretListener: NotebookCaretListener
    val caretListener: NotebookCaretListener get() = activeCaretListener

    val document = runReadAction {
        FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
    }

    private val finishedFiles = mutableSetOf<Int>()

    val targetIndexes: Set<Int>
        get() = fileToInjectionData.values.mapTo(mutableSetOf()) { it.second }
    val finishedHighlighting: Set<Int>
        get() = finishedFiles
    val remainingIndexesToProcess: Set<Int>
        get() = targetIndexes - finishedFiles

    fun tryGetKnownHostFor(file: PsiFile): PsiLanguageInjectionHost? {
        if (file !is KtFile) return null
        return fileToInjectionData.getOrElse(file, defaultValue = { null })?.first
    }

    fun isFileTarget(file: PsiFile): Boolean {
       return file == targetPsiFile
    }

    fun associateWithNewCaretListener(listener: NotebookCaretListener) {
        activeCaretListener = listener
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
                }
            }
        }
        this.completeRangeInd = completeRangeInd
    }

    fun clearState() {
        targetPsiFile = null
        fileToInjectionData.clear()
        finishedFiles.clear()
    }

    // Convenience methods
    fun scheduleArbitraryUpdate(
        file: PsiFile, delayDelta: Long,
        afterRequest: () -> Unit = {},
        undoRequest: () -> Unit = {},
        action: CoroutineScope.() -> Unit = {}
    ) {
        highlightingRestarter.scheduleDelayedUpdate(document, file, delayDelta, afterRequest, undoRequest, action)
    }

    fun scheduleUpdateInFile(file: PsiFile?, delayDelta: Long = HL_DELAY_PAUSE + 20, mode: RestartMode = RestartMode.Regular) =
        file?.let {
            when (mode) {
                RestartMode.Regular -> {
                    highlightingRestarter.scheduleRegularUpdate(document, file, delayDelta)
                }
                else -> {
                    highlightingRestarter.scheduleScriptingUpdate(document, file, delayDelta)
                }
            }
        }

    fun finishedAnalysisForFile(psiFile: PsiFile, host: PsiLanguageInjectionHost?) {
        finishedFiles.addIfNotNull(fileToInjectionData[psiFile]?.second)
    }

    override fun dispose() {
        clearState()
    }
}


internal object NotebookHighlightingRestarter {
    private var updateJob: Job? = null
    private val regularUpdateScope = CoroutineScope(Dispatchers.Default)

    object UpdateSteps {
        internal val postScriptingUpdateStep: (Document?) -> Unit = {
            it?.getUserData(NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange)?.compareAndSet(false, true)
        }

        internal val undoScriptingUpdateStep: (Document?) -> Unit = {
            it?.getUserData(NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange)?.compareAndSet(true, false)
        }

        suspend inline fun performHLStartupTemplate(file: PsiFile, delayDelta: Long,
                                                    crossinline afterRequest: () -> Unit = {},
                                                    crossinline undoRequest: () -> Unit = {}) {
            val analyzer = DaemonCodeAnalyzer.getInstance(file.project) as DaemonCodeAnalyzerImpl
            delay(delayDelta)
            while (analyzer.isRunning) {
                delay(150)
            }
            runReadAction {
                file.restartAnalyzing()
            }
            afterRequest()
        }
    }

    inline fun scheduleDelayedUpdate(document: Document?, file: PsiFile, delayDelta: Long,
                                     crossinline afterRequest: () -> Unit = {},
                                     crossinline undoRequest: () -> Unit = {},
                                     crossinline action: CoroutineScope.() -> Unit = {}) {
        if (!shouldStartAfterPreChecks(file, null, afterRequest, undoRequest)) return
        regularUpdateScope.launch {
            delay(delayDelta)
            action()
        }
    }

    fun scheduleRegularUpdateNoChecks(file: PsiFile, delayDelta: Long = HL_DELAY_PAUSE) {
        regularUpdateScope.launch {
            delay(delayDelta)
            runReadAction { file.restartAnalyzing() }
        }
    }

    inline fun scheduleRegularUpdate(document: Document?, file: PsiFile, delayDelta: Long = HL_DELAY_PAUSE,
                                     crossinline afterRequest: () -> Unit = {},
                                     crossinline undoRequest: () -> Unit = {}) {
        if (!shouldStartAfterPreChecks(file, updateJob, afterRequest, undoRequest)) return
        updateJob = regularUpdateScope.launch {
            performHLStartupTemplate(file, delayDelta, afterRequest, undoRequest)
        }
    }

    fun scheduleScriptingUpdate(document: Document?, file: PsiFile, delayDelta: Long = 320) {
        val afterRequest:() -> Unit = {
            postScriptingUpdateStep(document)
        }
        if ((DaemonCodeAnalyzer.getInstance(file.project) as DaemonCodeAnalyzerImpl).isRestartToCompleteEssentialHighlightingRequested) {
            afterRequest()
            return
        }
        if (!shouldStartAfterPreChecks(file, updateJob, afterRequest = afterRequest, undoRequest = {
            document?.getUserData(NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange)?.compareAndSet(true, false)
        })) {
            return
        }

        updateJob = regularUpdateScope.launch {
            performHLStartupTemplate(file, delayDelta, afterRequest)
        }
    }
}



