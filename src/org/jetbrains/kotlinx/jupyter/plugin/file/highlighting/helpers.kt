// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.CaretListener
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
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.editor.fixers.end
import org.jetbrains.kotlin.idea.editor.fixers.start
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.invalidateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.editor.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.getErrorPresenceIndicator
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

@TestOnly
fun markHostAsCompleteAnalysisTarget(document: Document, host: PsiLanguageInjectionHost) {
    document.putUserData(NotebookHighlightingUtilityObject.CompleteHighlightingRange, host.textRange)
}

internal object NotebookHighlightingUtilityObject {
    const val notebookInjectedFileExtension: String = "jupyter.kts"
    private const val notebookInjectedMetaFileExtension: String = "juktm"
    private const val notebookDocumentFileExtension: String = "ipynb"
    private val updateScope = CoroutineScope(Dispatchers.Default)
    private val LOG = thisLogger()
    const val cellToHighlightLimit: Int = 15

    const val scriptingMissingDependencyPrefix = "MISSING"
    const val scriptingMissingClassError = "${scriptingMissingDependencyPrefix}_SCRIPT_RECEIVER_CLASS"
    @NlsSafe
    const val scriptingMissingBaseClassError = "[${scriptingMissingDependencyPrefix}_SCRIPT_BASE_CLASS]"

    // to unify
    val NotebookDocumentTargetRanges = Key.create<Collection<Int>>("notebook.document.target.ranges")

    /**
     * Used to determine which ranges are scheduled for HL.
     * This is needed to invalidate old ones
     */
    internal val NotebookQueuedTargetRanges = Key.create<MutableSet<Int>>("notebook.document.target.queued")
    internal val NotebookDocumentStructureNontrivialChanged = Key.create<AtomicReference<Boolean>>("notebook.document.structure.changed")
    internal val NotebookCellsUpdatesAllowedToChange = Key.create<AtomicReference<Boolean>>("notebook.cells.updates.allowed.to.change")

    internal val NotebookEditorCaretListenerReferenceKey = Key.create<CaretListener>("editor.notebook.stored.listener")

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    internal val RenamingEnclosedRange: Key<Collection<TextRange>> = Key.create("notebook.after.rename.changed.range")
    internal val CompleteHighlightingRange: Key<TextRange> = Key.create("notebook.document.errors.analysis.range")
    internal val NonTargetHostErrorMark: Key<Boolean> = Key.create("injected.element.actual.errors.registry")

    internal val ReformatDocumentActionTargets: Key<MutableSet<Int>> = Key.create("notebook.refactor.action.triggered")

    internal val NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX: Key<Int> = Key.create("notebook.document.target.cell.ind")

    fun scheduleUpdateNoChecks(file: PsiFile, delayDelta: Long = 320, afterRequest: () -> Unit = {}) {
        updateScope.async {
            delay(delayDelta)
            while (DaemonCodeAnalyzerStatusService.getInstance(file.project).daemonRunning) {
                delay(200)
            }
            file.restartAnalyzing()
            afterRequest()
        }
    }

    fun PsiFile.scheduleHLUpdate(document: Document?) {
        scheduleUpdateNoChecks(this) {
            document?.getUserData(NotebookCellsUpdatesAllowedToChange)?.compareAndSet(false, true)
        }
    }

    fun scheduleUpdateLater(file: PsiFile, delayDelta: Long = 700) {
        updateScope.async {
            val manager = ScriptDefinitionsManager.getInstance(file.project)
            var isReady = manager.isReady()
            while (!isReady) {
              delay(delayDelta)
                isReady = manager.isReady()
            }
          invokeLater {
            file.restartAnalyzing()
          }
        }
    }

    fun getCompleteAnalysisRangeForWholeNotebook(injectedFile: PsiFile): TextRange? {
        val project = injectedFile.project
        val manager = InjectedLanguageManager.getInstance(project)
        val topLevelFile = manager.getTopLevelFile(injectedFile)
        return topLevelFile.toDocument(project)?.let {
            synchronized(it) {
                it.getUserData(CompleteHighlightingRange)
            }
        }
    }

    fun isLooksLikeNotebookDocument(document: Document): Boolean =
        FileDocumentManager.getInstance().getFile(document)?.extension == notebookDocumentFileExtension

    fun looksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == notebookDocumentFileExtension

    fun Document.invalidateStateAfterCellExecution(executedCell: PsiLanguageInjectionHost? = null, executedCellInd: Int? = null) {
        putUserData(RenamingEnclosedRange, null)
        putUserData(NotebookDocumentTargetRanges, null)
        putUserData(CompleteHighlightingRange, null)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, executedCellInd)
    }

    fun getCellRangesInDocumentOrNull(notebookCells: List<JupyterPsiCell>, targets: Collection<Int>?): List<TextRange>? = if (targets?.isNotEmpty() == true) {
        targets.mapNotNull { notebookCells.getOrNull(it)?.textRange }
    } else null

    fun Document.reactOnThemeChangedEvent() {
        putUserData(NotebookDocumentTargetRanges, null)
        putUserData(CompleteHighlightingRange, null)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
        putUserData(RenamingEnclosedRange, null)
    }

    fun PsiLanguageInjectionHost.getErrorPresenceIndicator() = getUserData(InjectedHostHasErrors)

    fun Document.retrieveCellIntervalUnderCaret(virtualFile: VirtualFile, project: Project): NotebookCellLines.Interval? {
        val editor = (FileEditorManager.getInstance(project).getSelectedEditor(virtualFile) as? JupyterFileEditor)?.editor
        val caretOffset = editor?.caretModel?.offset ?: return null
        val lineNumber = getLineNumber(caretOffset)
        return editor.getCell(lineNumber)
    }

    /**
     * [require] ReadAction
     * [require] EDT thread
     */
    fun resetSessionMetaInformation(document: Document, vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        val cellOrdinal = FileEditorManager.getInstance(project).getSelectedEditor(vFile)?.safeAs<TextEditor>()?.let {
            val editor = it.editor
            val pos = editor.caretModel.logicalPosition
            val cell = editor.getCell(min(pos.line, document.lineCount - 1))
            cell.ordinal
        }
        LOG.info("Resetting session meta information")
        val cell = cellOrdinal?.let { vFile.toPsiFile(project)?.getNotebookCellList()?.getOrNull(it) }
        document.invalidateStateAfterCellExecution(cell, cellOrdinal)
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val psiFile = vFile.toPsiFile(project)
        psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        val cellList = psiFile?.getNotebookCellList()
        cellList?.forEach {
            it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
            it.putUserData(InjectedHostHasErrors, null)
            invalidateTypeHintsRegistry(it)
            injectedManager.getInjectedPsiFiles(it)?.firstOrNull { f ->
                f.first is KtFile
            }?.first?.putUserData(NonTargetHostErrorMark, null)
        }

        if (wouldShowNotification) {
          NotebookNotificationUtility.showKernelRestart(project)
        }
        invokeAndWaitIfNeeded {
            LOG.info("Requesting restart of scripting support after session restart")
            JupyterKtScriptingSupport.getInstance(project).update()
        }
        invokeLater {
            (document.getUserData(NotebookEditorCaretListenerReferenceKey) as? NotebookCaretListener)
                ?.resetState()
            document.getUserData(NotebookQueuedTargetRanges)?.addAll(
                psiFile?.getNotebookCellList()?.indices?.toList() ?: listOf()
            )
            psiFile?.restartAnalyzing()
        }
    }
}



class InjectedFileHighlightingHelper(val injectedFile: PsiFile) {
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
        targetHost = injectedManager.getInjectionHost(injectedFile) ?: return false
        completeAnalysisRange = NotebookHighlightingUtilityObject.getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        isShouldHighlightErrors = completeAnalysisRange?.contains(targetHost.textRange) ?:
                (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange))


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
            if (seenInfosOffsets.add(el.range.start) && seenInfosOffsets.add(el.range.end)) {
                holder.add(el)
            }
        }
    }

    fun isShouldAcceptDiagnostic(elem: Diagnostic): Boolean {
        val info = elem.factory.name
        if (info.startsWith(NotebookHighlightingUtilityObject.scriptingMissingBaseClassError)) {
            NotebookNotificationUtility.showAbsentInitialBaseDependenciesInfo(elem.psiFile.project)
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

    fun convertToShadowedDeclaration(info: HighlightInfo): HighlightInfo {
        val n = HighlightInfo.newHighlightInfo(shadowedSymbolSeverity)
            .range(info.range)
            .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
            .needsUpdateOnTyping(info.needUpdateOnTyping())
            .fillInProperDescription(info)
            .group(0)

        return if (info.isAfterEndOfLine)
            n.endOfLine().createUnconditionally()
        else n.createUnconditionally()
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


