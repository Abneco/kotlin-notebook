// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.debug.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.invalidateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference


internal object NotebookHighlightingUtilityObject {
    const val NOTEBOOK_INJECTED_FILE_EXTENSION: String = "jupyter.kts"
    private const val NOTEBOOK_DOCUMENT_FILE_EXTENSION: String = "ipynb"
    private val updateScope = CoroutineScope(Dispatchers.Default)
    private val LOG = thisLogger()

    const val SCRIPTING_MISSING_DEPENDENCY_PREFIX = "MISSING"
    const val SCRIPTING_MISSING_CLASS_ERROR = "${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_RECEIVER_CLASS"
    @NlsSafe
    const val SCRIPTING_MISSING_BASE_CLASS_ERROR = "[${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_BASE_CLASS]"

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    internal val NonTargetHostErrorMark: Key<Boolean> = Key.create("injected.element.actual.errors.registry")

    inline fun shouldStartAfterPreChecks(file: PsiFile, associatedJob: Job?,
                                         crossinline afterRequest: () -> Unit = {},
                                         crossinline undoRequest: () -> Unit = {}): Boolean {
        val analyzer = DaemonCodeAnalyzerStatusService.getInstance(file.project)
        if (associatedJob?.isActive == true) {
            undoRequest()
            return false
        }
        if (analyzer.daemonRunning) {
            afterRequest()
            return false
        }

        return true
    }

    fun getCompleteAnalysisRangeForWholeNotebook(injectedFile: PsiFile): TextRange? {
        val project = injectedFile.project
        val manager = InjectedLanguageManager.getInstance(project)
        val topLevelFile = manager.getTopLevelFile(injectedFile)
        topLevelFile.virtualFile.toBackedNotebookFile()
        return topLevelFile.virtualFile.toBackedNotebookFile()?.let {
            NotebookHighlightingService.getForFile(project, it).dataController.completeHighlightingRange
        }
    }

    fun looksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == NOTEBOOK_DOCUMENT_FILE_EXTENSION

    fun getCellRangesInDocumentOrNull(notebookCells: List<JupyterPsiCell>, targets: Collection<Int>?): List<TextRange>? = if (targets?.isNotEmpty() == true) {
        targets.mapNotNull { notebookCells.getOrNull(it)?.textRange }
    } else null

    fun BackedNotebookVirtualFile.reactOnThemeChangedEvent(project: Project) {
        NotebookHighlightingService.getForFile(project, this).dataController.update {
            completeHighlightingRange = null
            notebookDocumentTargetRanges = null
            notebookChangedCellIndex = null
            renamingEnclosedRange = null
            notebookRangesQueuedForHL?.addAll(
              file.toPsiFile(project)?.getNotebookCells()?.indices?.toList() ?: emptyList()
            )
        }
    }

    fun PsiLanguageInjectionHost.getErrorPresenceIndicator() = getUserData(InjectedHostHasErrors)

    fun Document.retrieveCellIntervalUnderCaret(virtualFile: VirtualFile, project: Project): NotebookCellLines.Interval? {
        val editor = (FileEditorManager.getInstance(project).getSelectedEditor(virtualFile) as? JupyterFileEditor)?.editor
        val caretOffset = editor?.caretModel?.offset ?: return null
        val lineNumber = getLineNumber(caretOffset)
        return editor.getCell(lineNumber)
    }

    /**
     * [get] ReadAction
     * [get] EDT
     */
    fun resetSessionMetaInformation(vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        if (project.isDisposed) return

        LOG.info("Resetting session meta information")
        val backedFile = vFile.toBackedNotebookFile()

        val hlManager = highlightingManagerFor(project, vFile)

        if (project.isDisposed) return
        val (psiFile, cells) = runReadAction {
            val psiFile = vFile.toPsiFile(project)
            val cells = psiFile?.getNotebookCells()
            hlManager?.dataController?.invalidateStateAfterCellExecution(null)
            val injectedManager = InjectedLanguageManager.getInstance(project)
            psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
            cells?.forEach {
                it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
                it.putUserData(InjectedHostHasErrors, null)
                invalidateTypeHintsRegistry(it)
                injectedManager.getInjectedPsiFiles(it)?.firstOrNull { f ->
                    f.first is KtFile
                }?.first?.putUserData(NonTargetHostErrorMark, null)
            }
            psiFile to cells
        }
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }
        backedFile?.let {
            project.messageBus
                .syncPublisher(NotebookSessionEventListener.TOPIC)
                .sessionRestarted(it)
        }
        if (wouldShowNotification) {
            NotebookNotificationUtility.kernelRelatedFactory
                .showKernelRestart(project)
        }

        runInEdt { // we want to ensure that this part will be executed on the dispatch thread
            if (project.isDisposed) return@runInEdt

            LOG.info("Requesting restart of scripting support after session restart")
            JupyterCompilerService.getInstance(project).requestScriptingUpdate()
        }
        if (project.isDisposed) return
        runReadAction {
            hlManager?.let { manager ->
                manager.resetCaretListenerState()
                manager.dataController.notebookRangesQueuedForHL?.addAll(
                    cells?.indices?.toList() ?: listOf()
                )
            }
            if (psiFile != null) {
                NotebookHighlightingRestarter.scheduleRegularUpdateNoChecks(psiFile, delayDelta = 2000)
            }
        }
    }

    fun highlightingManagerFor(project: Project, file: VirtualFile): NotebookHighlightingManager? {
        return file.let(BackedNotebookVirtualFile::takeIfBacked)?.let {
            NotebookHighlightingService.getForFile(project, it)
        }
    }
}



class InjectedFileHighlightingHelper(private val injectedFile: PsiFile) {
    private val project = injectedFile.project
    private lateinit var targetHost: PsiLanguageInjectionHost
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private var completeAnalysisRange: TextRange? = null
    val topLevelFile: PsiFile? = injectedManager.getTopLevelFile(injectedFile)
    init {
        assert(tryUpdateCurrentInjectedFileTarget())
    }
    private var isShouldHighlightErrors: Boolean = false

    private fun tryUpdateCurrentInjectedFileTarget(): Boolean {
        val highlightingManager =
            topLevelFile?.virtualFile?.let(BackedNotebookVirtualFile::takeIfBacked)
                ?.let { NotebookHighlightingService.getForFile(project, it) }
        targetHost = highlightingManager?.tryGetKnownHostFor(injectedFile)
            ?: injectedManager.getInjectionHost(injectedFile) ?: return false

        isShouldHighlightErrors = highlightingManager?.isFileTarget(injectedFile)
            ?: checkIfHostIsTargetManually()

        return true
    }

    private fun checkIfHostIsTargetManually(): Boolean {
        completeAnalysisRange = NotebookHighlightingUtilityObject.getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        return completeAnalysisRange?.contains(targetHost.textRange)
                ?:
            (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange))
    }

    val isCurrentFileTarget: Boolean get() = isShouldHighlightErrors

    fun markTargetHost() {
        injectedFile.putUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorMark, if (isShouldHighlightErrors) null else true)
    }

    fun applyReceivedHighlightInfos(foundData: Collection<HighlightInfo>, holder: HighlightInfoHolder) {
        val errorRef = targetHost.getErrorPresenceIndicator()
            ?: AtomicReference(foundData.isNotEmpty()).also { targetHost.putUserData(NotebookHighlightingUtilityObject.InjectedHostHasErrors, it) }

        val seenInfosOffsets = mutableSetOf<Int>()
        if (foundData.isNotEmpty()) errorRef.set(true)
        else errorRef.compareAndSet(true, false)

        for (el in foundData) {
            if (seenInfosOffsets.add(el.startOffset) && seenInfosOffsets.add(el.endOffset)) {
                holder.add(el)
            }
        }
    }

    fun isShouldAcceptDiagnostic(elem: Diagnostic): Boolean {
        val info = elem.factory.name
        if (info.startsWith(NotebookHighlightingUtilityObject.SCRIPTING_MISSING_BASE_CLASS_ERROR)) {
            NotebookNotificationUtility.kernelRelatedFactory.showAbsentInitialBaseDependenciesInfo(elem.psiFile.project)
            return false
        }
        return info != NotebookHighlightingUtilityObject.SCRIPTING_MISSING_CLASS_ERROR
    }

}


internal object HighlightInfoManipulator {
    @NlsSafe
    private const val shadowedSymbolDescription = "Not yet provided symbol"
    @NlsSafe
    private const val improperSymbolDescription = "Improper usage"
    private val shadowedSymbolSeverity = HighlightInfo.convertSeverity(HighlightSeverity.INFORMATION)

    fun convertToShadowedDeclaration(diagnostic: Diagnostic): HighlightInfo? {
        if (diagnostic.severity != Severity.ERROR) return null
        val element = diagnostic.psiElement

        return HighlightInfo.newHighlightInfo(shadowedSymbolSeverity)
            .range(element.textRange)
            .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
            .needsUpdateOnTyping(false)
            .group(0)
            .fillInProperDescription(diagnostic)
            .createUnconditionally()
    }

    private fun HighlightInfo.Builder.fillInProperDescription(diagnostic: Diagnostic): HighlightInfo.Builder {
        return if (diagnostic.factory.name == Errors.UNRESOLVED_REFERENCE.name)
            this.description(shadowedSymbolDescription).unescapedToolTip(shadowedSymbolDescription)
        else this.escapedToolTip(improperSymbolDescription)
    }
}


