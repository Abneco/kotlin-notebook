// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeLater
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
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.editor.fixers.end
import org.jetbrains.kotlin.idea.editor.fixers.start
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
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

    const val scriptingMissingClassError = "MISSING_SCRIPT_RECEIVER_CLASS"
    @NlsSafe
    const val scriptingMissingBaseClassError = "[MISSING_SCRIPT_BASE_CLASS]"

    @JvmField
    val NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE = Key.create<TextRange>("notebook.document.target.range")
    // to unify
    val NotebookDocumentTargetRanges = Key.create<Collection<TextRange>>("notebook.document.target.ranges")

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    internal val DocumentScriptManagerUpdateNeeded = Key.create<AtomicReference<Boolean>>("document.script.def.manager.update")
    internal val RenamingEnclosedRange: Key<Collection<TextRange>> = Key.create("notebook.after.rename.changed.range")
    internal val CompleteHighlightingRange: Key<TextRange> = Key.create("notebook.document.errors.analysis.range")
    internal val NonTargetHostErrorRegistry: Key<MutableCollection<HighlightInfo>> = Key.create("injected.element.actual.errors.registry")

    internal val ReformatDocumentActionTargets: Key<MutableSet<Int>> = Key.create("notebook.refactor.action.triggered")

    internal val NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX: Key<Int> = Key.create("notebook.document.target.cell.ind")

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

    fun isLooksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == notebookDocumentFileExtension

    fun Document.invalidateStateAfterCellExecution(executedCell: PsiLanguageInjectionHost? = null) {
        putUserData(RenamingEnclosedRange, null)
        putUserData(NotebookDocumentTargetRanges, null)
        putUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE, executedCell?.textRange)
        putUserData(CompleteHighlightingRange, executedCell?.textRange)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
    }

    fun Document.reactOnThemeChangedEvent() {
        putUserData(NotebookDocumentTargetRanges, null)
        putUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE, null)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
        putUserData(RenamingEnclosedRange, null)
    }

    /**
     * [require] ReadAction
     * [require] EDT thread
     */
    fun resetSessionMetaInformation(document: Document, vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        val cell = FileEditorManager.getInstance(project).getSelectedEditor(vFile)?.safeAs<TextEditor>()?.let {
            val editor = it.editor
            val pos = editor.caretModel.logicalPosition
            val cell = editor.getCell(min(pos.line, document.lineCount - 1))
            vFile.toPsiFile(project)?.getNotebookCellList()?.get(cell.ordinal)
        }
        document.invalidateStateAfterCellExecution(cell)
        val psiFile = vFile.toPsiFile(project)
        psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        psiFile?.getNotebookCellList()?.forEach {
            it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        }
        if (wouldShowNotification) {
          NotebookNotificationUtility.showKernelRestart(project)
        }
        invokeLater {
            psiFile?.restartAnalyzing()
        }
    }
}



class InjectedFileHighlightingHelper(val injectedFile: PsiFile, isFirstPass: Boolean) {
    private val project = injectedFile.project
    private lateinit var targetHost: PsiLanguageInjectionHost
    private var errorRegistry: Collection<HighlightInfo>? = null
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private var completeAnalysisRange: TextRange? = null
    init {
        assert(tryUpdateCurrentInjectedFileTarget(isFirstPass))
    }
    private var isShouldHighlightErrors: Boolean = false

    private fun tryUpdateCurrentInjectedFileTarget(completeUpdate: Boolean): Boolean {
        targetHost = injectedManager.getInjectionHost(injectedFile) ?: return false
        if (!completeUpdate) {
            errorRegistry = synchronized(injectedFile) {
                injectedFile.getUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorRegistry)
            }
            isShouldHighlightErrors = errorRegistry == null
            return true
        }
        completeAnalysisRange = NotebookHighlightingUtilityObject.getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        isShouldHighlightErrors = completeAnalysisRange?.contains(targetHost.textRange) ?:
                (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange.shiftLeft(1)))
        //if (isShouldHighlightErrors) {
        //    println("Should highlight errors for ${injectedFile.name} with range: ${targetHost?.range}")
        //} else println("should not for ${injectedFile.name} with range: ${targetHost?.range}")


        return true
    }

    fun markTargetHost() {
        injectedFile.putUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorRegistry, if (isShouldHighlightErrors) null else mutableSetOf())
    }

    fun updateHolderOrProvided(holder: HighlightInfoHolder) {
        if (isShouldHighlightErrors) {
            return
        }
        val errorRef = targetHost.getUserData(NotebookHighlightingUtilityObject.InjectedHostHasErrors)
            ?: AtomicReference(true).also { targetHost.putUserData(NotebookHighlightingUtilityObject.InjectedHostHasErrors, it) }
        val registry = errorRegistry ?: return

        val seenInfos = mutableSetOf<Int>()
        if (registry.isNotEmpty()) {
            errorRef.set(true)
            for (el in registry) {
                if (el.severity == HighlightSeverity.ERROR && seenInfos.add(el.range.start) && seenInfos.add(el.range.end)) {
                    holder.add(HighlightInfoManipulator.convertToShadowedDeclaration(el))
                }
            }
        } else errorRef.compareAndSet(true, false)
    }

}


internal object HighlightInfoManipulator {
    @NlsSafe
    private const val shadowedSymbolDescription = "Not yet provided symbol"
    @NlsSafe
    private const val improperSymbolDescription = "Improper usage"
    private val shadowedSymbolSeverity = HighlightInfo.convertSeverity(HighlightSeverity.INFORMATION)

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

    private fun HighlightInfo.Builder.fillInProperDescription(info: HighlightInfo): HighlightInfo.Builder {
        return if (info.description == Errors.UNRESOLVED_REFERENCE.name)
                  this.description(shadowedSymbolDescription).unescapedToolTip(shadowedSymbolDescription)
               else this.escapedToolTip(improperSymbolDescription)
    }
}


