// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.utils

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jupyter.helper.selectedInterval
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.editor.codeInsight.hints.PsiHostTypeHintsInvalidator
import com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructurePerFileTracker.Companion.CELL_CLASS_NAME
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.kotlinNotebookLogger
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.removeUserData
import org.jetbrains.kotlin.psi.KtFile

object NotebookHighlightingUtilityObject {
    const val SCRIPTING_MISSING_DEPENDENCY_PREFIX: String = "MISSING"
    const val SCRIPTING_MISSING_CLASS_ERROR: String = "${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_RECEIVER_CLASS"

    @NlsSafe
    const val SCRIPTING_MISSING_BASE_CLASS_ERROR: String = "[${SCRIPTING_MISSING_DEPENDENCY_PREFIX}_SCRIPT_BASE_CLASS]"

    val NonTargetHostErrorMark: Key<Boolean> = Key.create("injected.element.actual.errors.registry")

}

internal fun BackedNotebookVirtualFile.reactOnThemeChangedEvent(project: Project) {
    NotebookHighlightingService.getForFile(project, this).restartAnalysing()
}

internal suspend fun cleanupKernelSession(
    kernelHandler: KernelRunnableHandler,
) {
    if (!kernelHandler.isVerified) return
    val notebookFile = kernelHandler.notebookVirtualFile
    val project = kernelHandler.project

    resetSessionMetaInformation(project, notebookFile)
    JupyterExecutionManager.getInstance(project, notebookFile).killExecution()
}

private suspend fun resetSessionMetaInformation(
    project: Project,
    backedNotebookVirtualFile: BackedNotebookVirtualFile,
) {
    if (project.isDisposed) return

    kotlinNotebookLogger.info("Resetting session meta information")

    val virtualFile = backedNotebookVirtualFile.file
    val compilerService = JupyterCompilerService.getInstance(project)

    readAction {
        if (project.isDisposed) return@readAction
        val psiFile = virtualFile.findPsiFile(project)
        compilerService.remove(backedNotebookVirtualFile)
        val cells = psiFile?.getNotebookCells()
        val injectedManager = InjectedLanguageManager.getInstance(project)
        psiFile?.removeUserData(CELL_CLASS_NAME)

        cells?.forEach { cell ->
            cell.removeUserData(CELL_CLASS_NAME)
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

fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(lhs)

val Editor.getSelectedCellIndex: Int?
    get() {
        return selectedInterval?.ordinal
    }