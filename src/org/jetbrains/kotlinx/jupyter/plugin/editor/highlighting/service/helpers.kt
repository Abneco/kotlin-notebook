// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.invalidateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution.KotlinNotebookCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.util.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min


internal object NotebookHighlightingUtilityObject {
    const val notebookInjectedFileExtension: String = "jupyter.kts"
    private const val notebookInjectedMetaFileExtension: String = "juktm"
    private const val notebookDocumentFileExtension: String = "ipynb"
    private val updateScope = CoroutineScope(Dispatchers.Default)
    private val LOG = thisLogger()

    const val scriptingMissingDependencyPrefix = "MISSING"
    const val scriptingMissingClassError = "${scriptingMissingDependencyPrefix}_SCRIPT_RECEIVER_CLASS"
    @NlsSafe
    const val scriptingMissingBaseClassError = "[${scriptingMissingDependencyPrefix}_SCRIPT_BASE_CLASS]"

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    internal val NonTargetHostErrorMark: Key<Boolean> = Key.create("injected.element.actual.errors.registry")

    inline fun shouldStartAfterPreChecks(file: PsiFile, associatedJob: Job?,
                                         crossinline afterRequest: () -> Unit = {},
                                         crossinline undoRequest: () -> Unit = {}): Boolean {
        val analyzer = (DaemonCodeAnalyzer.getInstance(file.project) as DaemonCodeAnalyzerImpl)
        if (associatedJob?.isActive == true) {
            undoRequest()
            return false
        }
        if (analyzer.isRunning) {
            afterRequest()
            return false
        }

        return true
    }

    fun scheduleUpdateLater(file: PsiFile, delayDelta: Long = 700) {
        updateScope.async {
            val manager = ScriptDefinitionsManager.getInstance(file.project)
            var isReady = manager.isReady()
            while (!isReady) {
              delay(delayDelta)
                isReady = manager.isReady()
            }
            readAction {
                file.restartAnalyzing()
            }
        }
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

    fun isLooksLikeNotebookDocument(document: Document): Boolean =
        FileDocumentManager.getInstance().getFile(document)?.extension == notebookDocumentFileExtension

    fun looksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == notebookDocumentFileExtension

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
                file.toPsiFile(project)?.getNotebookCellList()?.indices?.toList() ?: emptyList()
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
    fun resetSessionMetaInformation(document: Document, vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        val cellOrdinal = invokeAndWaitIfNeeded {
            FileEditorManager.getInstance(project).getSelectedEditor(vFile)?.safeAs<TextEditor>()?.let {
                val editor = it.editor
                val pos = editor.caretModel.logicalPosition
                val cell = editor.getCell(min(pos.line, document.lineCount - 1))
                cell.ordinal
            }
        }
        LOG.info("Resetting session meta information")
        val backedFile = vFile.toBackedNotebookFile()

        val hlManager = highlightingManagerFor(project, vFile)

        val (psiFile, cells) = runReadAction {
            val psiFile = vFile.toPsiFile(project)
            val cells = psiFile?.getNotebookCellList()

            hlManager?.dataController?.invalidateStateAfterCellExecution(cellOrdinal)
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
        backedFile?.let {
            KotlinNotebookCellExecutionCallbackFactory.getInstance().sessionRestarted(it)
            hlManager?.sessionRestarted()
        }
        if (wouldShowNotification) {
            NotebookNotificationUtility.kernelRelatedFactory
                .showKernelRestart(project)
        }

        invokeAndWaitIfNeeded { // we want to ensure that this part will be executed on the dispatch thread
            LOG.info("Requesting restart of scripting support after session restart")
            hlManager?.beforeScriptingUpdate()
                ?:
            JupyterKtScriptingSupport.update(project)
        }
        runReadAction {
            hlManager?.let { manager ->
                manager.resetCaretListenerState()
                manager.dataController.notebookRangesQueuedForHL?.addAll(
                    cells?.indices?.toList() ?: listOf()
                )
            }
            psiFile?.restartAnalyzing()
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
            ?: run { // a bit heavy, avoid, if possible
                completeAnalysisRange = NotebookHighlightingUtilityObject.getCompleteAnalysisRangeForWholeNotebook(injectedFile)
                completeAnalysisRange?.contains(targetHost.textRange) ?:
                (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange))
            }


        return true
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
        if (info.startsWith(NotebookHighlightingUtilityObject.scriptingMissingBaseClassError)) {
            NotebookNotificationUtility.kernelRelatedFactory.showAbsentInitialBaseDependenciesInfo(elem.psiFile.project)
            return false
        }
        return info != NotebookHighlightingUtilityObject.scriptingMissingClassError
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

    private fun HighlightInfo.Builder.fillInProperDescription(info: HighlightInfo): HighlightInfo.Builder {
        return if (info.description == Errors.UNRESOLVED_REFERENCE.name)
                  this.description(shadowedSymbolDescription).unescapedToolTip(shadowedSymbolDescription)
               else this.escapedToolTip(improperSymbolDescription)
    }
}


