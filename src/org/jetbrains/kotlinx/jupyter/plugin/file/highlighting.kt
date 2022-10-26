// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingFilesFilterProvider
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE
import com.intellij.codeInsight.daemon.impl.NotebookInjectedCodeUtility.NOTEBOOK_FILE_ANALYSIS_DONE_KEY
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import java.util.function.Predicate


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookInjectedCodeUtility
    private val rangeMargin = 12
    private val dummyTextChangeRange = TextRange(0, 0)

    override fun reduceRange(file: PsiFile, editor: Editor): TextRange? {
        if (!notebookCodeUtility.isLooksLikeNotebookFile(file)) return null

        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null

        return synchronized(document) {
            if (document.getUserData(NOTEBOOK_FILE_ANALYSIS_DONE_KEY) != null) {
                return dummyTextChangeRange
            }
            document.getUserData(NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE)
        }?.let {
            TextRange(it.startOffset, it.endOffset + rangeMargin)
        }
        //val cellList = (jupyterFile.children.first() as? JupyterNotebook)?.psiCellList
    }

}


internal class KotlinNotebookInjectedFilesFilterProvider: InjectedLanguageHighlightingFilesFilterProvider {
    private val notebookCodeUtility = NotebookInjectedCodeUtility

    override fun provideFilterForInjectedFiles(file: PsiFile, editor: Editor): Predicate<PsiFile>? {
        if (!notebookCodeUtility.isLooksLikeNotebookFile(file)) return null
        return notebookInjectedKotlinFileFilter
    }

    companion object {
        private val notebookInjectedKotlinFileFilter = Predicate<PsiFile> { file ->
            file is KtFile
        }
    }
}