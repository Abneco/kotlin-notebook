// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.editor.codeInsight.hints.PsiHostTypeHintsInvalidator
import com.intellij.kotlin.jupyter.core.editor.find.NotebookReferenceFinder
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingManager
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.InjectedHostHasErrors
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.kotlinNotebookLogger
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import java.util.concurrent.atomic.AtomicReference

object NotebookHighlightingUtilityObject {
    const val SCRIPTING_MISSING_DEPENDENCY_PREFIX: String = "MISSING"
    const val SCRIPTING_MISSING_CLASS_ERROR: String = "${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_RECEIVER_CLASS"
    @NlsSafe
    const val SCRIPTING_MISSING_BASE_CLASS_ERROR: String = "[${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_BASE_CLASS]"

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    val NonTargetHostErrorMark: Key<Boolean> = Key.create("injected.element.actual.errors.registry")

    inline fun shouldStartAfterPreChecks(file: PsiFile, associatedJob: Job?,
                                         crossinline afterRequest: () -> Unit = {},
                                         crossinline undoRequest: () -> Unit = {}): Boolean {
        val analyzer = DaemonAnalyzerStatusService.getInstance(file.project)
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
            file.findPsiFile(project)?.getNotebookCells()?.indices?.toList() ?: emptyList()
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

internal suspend fun cleanupKernelSession(
  kernelHandler: KernelRunnableHandler,
) {
    if (!kernelHandler.isVerified) return
    val notebookFile = kernelHandler.notebookVirtualFile ?: return
    val project = kernelHandler.project

    resetSessionMetaInformation(project, notebookFile)
    if (!project.isDisposed) {
        JupyterRuntimeService.getInstance(project).clearRuntime(notebookFile.file).join()
    }
}

private suspend fun resetSessionMetaInformation(
    project: Project,
    backedNotebookVirtualFile: BackedNotebookVirtualFile,
) {
    if (project.isDisposed) return

    kotlinNotebookLogger.info("Resetting session meta information")

    val virtualFile = backedNotebookVirtualFile.file
    val hlManager = NotebookHighlightingService.getForFile(project, backedNotebookVirtualFile)
    val compilerService = JupyterCompilerService.getInstance(project)

    readAction {
        if (project.isDisposed) return@readAction
        val psiFile = virtualFile.findPsiFile(project)
        compilerService.removeSession(backedNotebookVirtualFile)
        val cells = psiFile?.getNotebookCells()
        hlManager.dataController.invalidateStateAfterCellExecution(null)
        val injectedManager = InjectedLanguageManager.getInstance(project)
        psiFile?.removeUserData(NotebookReferenceFinder.CELL_CLASS_NAME)

        cells?.forEach { cell ->
            cell.removeUserData(NotebookReferenceFinder.CELL_CLASS_NAME)
            cell.removeUserData(InjectedHostHasErrors)
            PsiHostTypeHintsInvalidator.invalidateTypeHintsRegistry(cell)

            injectedManager.getInjectedPsiFiles(cell)?.forEach { elementWithRange ->
                val psiElement = elementWithRange.first
                if (psiElement is KtFile) {
                    psiElement.removeUserData(NonTargetHostErrorMark)
                }
            }
        }
    }

    kotlinNotebookLogger.info("Requesting restart of scripting support after session restart")
    JupyterCompilerService.getInstance(project).requestScriptingUpdate()
}

internal fun highlightingManagerFor(project: Project, file: VirtualFile): NotebookHighlightingManager? {
    return file.let(BackedNotebookVirtualFile::takeIfBacked)?.let {
        NotebookHighlightingService.getForFile(project, it)
    }
}

fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(lhs)