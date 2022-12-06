// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showKernelRestart
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookHighlightingUtilityObject
    private val dummyTextChangeRange = TextRange(0, 0)

    override fun reduceRange(file: PsiFile, editor: Editor): TextRange? {
        if (!notebookCodeUtility.isLooksLikeNotebookFile(file)) return null

        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null

        val cells = file.getNotebookCellList()
        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(file.project),
                                                               InjectedLanguageManager.getInstance(file.project))

        return synchronized(document) {
            val afterRenaming = document.getUserData(RenamingEnclosedRange)
            if (afterRenaming != null) {
                return@synchronized afterRenaming
            }

            val cellInd = document.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)?.let { // notebook file is already rebuild
                cells?.get(it)
            }?.textRange
            val possibleRange = document.getUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE)
            if (cellInd != null && possibleRange?.endOffset != cellInd.endOffset) cellInd else possibleRange
        }?.let {
            TextRange(it.startOffset, it.endOffset + 1)
        }
    }

    private fun Collection<PsiLanguageInjectionHost>?.ensureScriptConfigurations(scriptingManager: ScriptConfigurationManager, manager: InjectedLanguageManager) {
        this?.forEach {
            manager.getInjectedPsiFiles(it)?.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                if (ktFile is KtFile) {
                    scriptingManager.getConfiguration(ktFile)
                }
            }
        }
    }

}

// might be done in HighlightingVisitorAdapter
internal class NotebookSelectedCellErrorsFilter: HighlightInfoFilter {
    private lateinit var host: PsiLanguageInjectionHost
    private var completeAnalysisHost: PsiLanguageInjectionHost? = null
    private var lastPsiFile: PsiFile? = null
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(notebookInjectedFileExtension)) return true
        val manager = InjectedLanguageManager.getInstance(file.project)
        if (file != lastPsiFile) {
            lastPsiFile = file
            if (!tryUpdateCurrentInjectedFileTarget(file, manager)) return true
        }
        if (!::host.isInitialized && !tryUpdateCurrentInjectedFileTarget(file, manager)) {
            return true
        }
        // comment for default behaviour
        //if (highlightInfo.severity == HighlightSeverity.ERROR
        //    && host != completeAnalysisHost) return false

        return true
    }

    private fun tryUpdateCurrentInjectedFileTarget(file: PsiFile, manager: InjectedLanguageManager): Boolean {
        host = manager.getInjectionHost(file) ?: return false
        val topLevel = manager.getTopLevelFile(file)
        completeAnalysisHost = JupyterCompilerService.getForFile(file.project, topLevel.virtualFile).completeAnalysisCellTarget
        return true
    }
}




class NotebookHighlightingCustomizer(private val project: Project, private val vFile: VirtualFile) {
    private val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    var targetCell: PsiLanguageInjectionHost? = null

    fun isHostTargetedForAnalysis(file: PsiFile): Boolean =
        injectedLanguageManager.getInjectionHost(file) == targetCell

    fun computeRestrictedRangeWithPrevTargetOrProvided(providedChangeRange: TextRange, delta: Int): TextRange {
        val cell = targetCell
        return if (cell == null) providedChangeRange
        else providedChangeRange.union(cell.textRange).grown(delta)
    }
}



internal fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(rhs)

internal object NotebookHighlightingUtilityObject {
    const val notebookInjectedFileExtension: String = "jupyter.kts"
    private const val notebookInjectedMetaFileExtension: String = "juktm"
    private const val notebookDocumentFileExtension: String = "ipynb"

    @JvmField
    val NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE = Key.create<TextRange>("notebook.document.ignored.range")
    @JvmField
    val ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY: Key<HighlightInfoHolder> = Key.create("injected.element.pass.info.holder")
    @JvmField
    val NOTEBOOK_FILE_ANALYSIS_DONE_KEY = Key.create<Boolean>("notebook.file.analysis.done")

    internal val RenamingEnclosedRange: Key<TextRange> = Key.create("notebook.after.rename.changed.range")
    internal val NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX: Key<Int> = Key.create("notebook.document.target.cell.ind")

    fun isLooksLikeNotebookDocument(document: Document): Boolean =
        FileDocumentManager.getInstance().getFile(document)?.extension == notebookDocumentFileExtension

    fun isLooksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == notebookDocumentFileExtension

    fun Document.invalidateStateAfterCellExecution(executedCell: PsiLanguageInjectionHost? = null) {
        putUserData(RenamingEnclosedRange, null)
        putUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE, executedCell?.textRange)
        putUserData(NOTEBOOK_FILE_ANALYSIS_DONE_KEY, null)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
    }


    /**
     * [require] ReadAction
     */
    fun resetSessionMetaInformation(document: Document, vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        document.invalidateStateAfterCellExecution()
        val psiFile = vFile.toPsiFile(project)
        psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        psiFile?.getNotebookCellList()?.forEach {
            it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        }
        if (wouldShowNotification) {
            showKernelRestart(project)
        }
        invokeLater {
            psiFile?.let { DaemonCodeAnalyzer.getInstance(project).restart(it) }
        }
    }
}