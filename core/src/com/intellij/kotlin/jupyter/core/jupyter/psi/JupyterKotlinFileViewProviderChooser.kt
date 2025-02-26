// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.psi

import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.notebooks.psi.jupyter.JupyterFileViewProviderFactory

class JupyterKotlinFileViewProviderChooser : JupyterFileViewProviderFactory.Chooser {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean
    ): FileViewProvider {
        return JupyterKotlinFileViewProvider(manager, file, true)
    }

    override fun isApplicable(file: VirtualFile, isBackProvider: Boolean): Boolean {
        if (isBackProvider) return false
        if (!file.exists()) return false
        return file.isKotlinNotebook
    }
}
