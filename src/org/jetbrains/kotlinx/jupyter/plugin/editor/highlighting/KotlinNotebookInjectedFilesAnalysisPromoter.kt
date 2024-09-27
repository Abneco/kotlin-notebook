// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.psi.FileViewProvider
import org.jetbrains.kotlin.idea.base.analysis.KotlinIdeInjectedFilesAnalysisPromoter
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

/**
 * Promotes analysis of injected [org.jetbrains.kotlin.psi.KtFile] inside Kotlin Notebook.
 *
 * If analysis is promoted, it will be analyzed by the base Kotlin visitor.
 */
internal class KotlinNotebookInjectedFilesAnalysisPromoter : KotlinIdeInjectedFilesAnalysisPromoter {
    override fun shouldRunAnalysisForInjectedFile(viewProvider: FileViewProvider): Boolean {
        val virtualFile = viewProvider.virtualFile
        return (virtualFile as? VirtualFileWindow)?.delegate?.isKotlinNotebook == true
    }
}