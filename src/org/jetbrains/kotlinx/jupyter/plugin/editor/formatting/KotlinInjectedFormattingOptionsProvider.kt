// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.formatting

import com.intellij.formatting.InjectedFormattingOptionsProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile

class KotlinInjectedFormattingOptionsProvider : InjectedFormattingOptionsProvider {
    override fun shouldDelegateToTopLevel(file: PsiFile): Boolean? {
        if (file !is KtFile) return null

        val project = file.project
        val injectedManager = InjectedLanguageManager.getInstance(project)
        if (injectedManager.getTopLevelFile(file) is JupyterFile) return false
        return null
    }
}
