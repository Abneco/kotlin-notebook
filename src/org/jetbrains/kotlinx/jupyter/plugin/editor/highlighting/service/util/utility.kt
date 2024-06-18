// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.NotebookTypeHintsRegistry.Companion.invalidateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingManager
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.InjectedHostHasErrors
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.LOG
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_FILE_EXTENSION
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference


internal object NotebookHighlightingUtilityObject {
    const val NOTEBOOK_INJECTED_FILE_EXTENSION: String = "jupyter.kts"
    internal const val NOTEBOOK_DOCUMENT_FILE_EXTENSION: String = "ipynb"
    internal val LOG = thisLogger()

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
}

internal fun PsiFile.looksLikeNotebookFile(): Boolean =
    fileType.defaultExtension == NOTEBOOK_DOCUMENT_FILE_EXTENSION

internal fun getCellRangesInDocumentOrNull(notebookCells: List<JupyterPsiCell>, targets: Collection<Int>?): List<TextRange>? = if (targets?.isNotEmpty() == true) {
    targets.mapNotNull { notebookCells.getOrNull(it)?.textRange }
} else null

internal fun BackedNotebookVirtualFile.reactOnThemeChangedEvent(project: Project) {
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

internal fun PsiLanguageInjectionHost.getErrorPresenceIndicator() = getUserData(InjectedHostHasErrors)

internal fun Document.retrieveCellIntervalUnderCaret(virtualFile: VirtualFile, project: Project): NotebookCellLines.Interval? {
    val editor = (FileEditorManager.getInstance(project).getSelectedEditor(virtualFile) as? JupyterFileEditor)?.editor
    val caretOffset = editor?.caretModel?.offset ?: return null
    val lineNumber = getLineNumber(caretOffset)
    return editor.getCell(lineNumber)
}

/**
 * [get] ReadAction
 * [get] EDT
 */
internal fun resetSessionMetaInformation(vFile: VirtualFile, project: Project) {
    if (project.isDisposed) return

    LOG.info("Resetting session meta information")

    val hlManager = highlightingManagerFor(project, vFile)

    if (project.isDisposed) return
    runReadAction {
        val psiFile = vFile.toPsiFile(project)
        val cells = psiFile?.getNotebookCells()
        hlManager?.dataController?.invalidateStateAfterCellExecution(null)
        val injectedManager = InjectedLanguageManager.getInstance(project)
        psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        cells?.forEach {
            it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
            it.putUserData(InjectedHostHasErrors, null)
            it.invalidateTypeHintsRegistry()
            injectedManager.getInjectedPsiFiles(it)?.firstOrNull { f ->
                f.first is KtFile
            }?.first?.putUserData(NonTargetHostErrorMark, null)
        }
    }

    LOG.info("Requesting restart of scripting support after session restart")
    JupyterCompilerService.getInstance(project).requestScriptingUpdate()
}

fun highlightingManagerFor(project: Project, file: VirtualFile): NotebookHighlightingManager? {
    return file.let(BackedNotebookVirtualFile::takeIfBacked)?.let {
        NotebookHighlightingService.getForFile(project, it)
    }
}
