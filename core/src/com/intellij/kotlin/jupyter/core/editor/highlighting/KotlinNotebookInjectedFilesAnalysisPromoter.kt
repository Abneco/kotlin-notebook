// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.analysis.KotlinIdeInjectedFilesAnalysisPromoter

/**
 * Promotes analysis of injected [org.jetbrains.kotlin.psi.KtFile] inside Kotlin Notebook.
 *
 * If analysis is promoted, it will be analyzed by the base Kotlin visitor.
 */
internal class KotlinNotebookInjectedFilesAnalysisPromoter : KotlinIdeInjectedFilesAnalysisPromoter {
    private fun createK2Handler(psiFile: PsiFile): Boolean {
        val backedNotebook = (psiFile.viewProvider.virtualFile as? VirtualFileWindow)?.delegate?.toBackedNotebookFile()
        return if (backedNotebook == null) {
            false
        } else {
            !NotebookHighlightingService.getForFile(psiFile.project, backedNotebook).isFileTarget(psiFile)
        }
    }

    override fun shouldRunAnalysisForInjectedFile(viewProvider: FileViewProvider): Boolean {
        val virtualFile = viewProvider.virtualFile
        return (virtualFile as? VirtualFileWindow)?.delegate?.isKotlinNotebook == true
    }

    override fun shouldRunOnlyEssentialHighlightingForInjectedFile(psiFile: PsiFile): Boolean {
        return createPluginModeAwareInstance(
            psiFile,
            { false },
            ::createK2Handler
        )
    }
}