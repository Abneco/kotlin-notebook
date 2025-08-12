// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.extensions

import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile

/**
 * The main entry-point for specifying which part of the notebook document should be rehighlighted.
 * Since we have injections, highlighting every file on each new pass would be too expensive.
 */
internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {

    override fun reduceRange(file: PsiFile, editor: Editor): List<TextRange>? {
        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null
        if (!NotebookCellLines.hasSupport(editor)) return null

        val notebookFile = jupyterFile.virtualFile.toKotlinNotebookBackedFile() ?: return null

        val cells = try {
            file.getNotebookCells()
        } catch (ex: IllegalStateException) {
            LOG.debug("Can't get Notebook cells list: $ex")
            null
        }
        val project = file.project
        ProgressManager.checkCanceled()

        cells?.ensureScriptConfigurations(
            InjectedLanguageManager.getInstance(project)
        )
        val highlightingManager = NotebookHighlightingService.Companion.getForFile(project, notebookFile)

        return synchronized(document) {
            highlightingManager.getRangesToHighlight(file, editor, cells).toList()
        }
    }

    private fun Collection<PsiLanguageInjectionHost>?.ensureScriptConfigurations(manager: InjectedLanguageManager) {
        this?.forEach {
            manager.getInjectedPsiFiles(it)?.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                if (ktFile is KtFile) {
                    JupyterCompilerService.getInstance(ktFile.project).ensureScriptConfiguration(ktFile.project, ktFile)
                }
            }
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}