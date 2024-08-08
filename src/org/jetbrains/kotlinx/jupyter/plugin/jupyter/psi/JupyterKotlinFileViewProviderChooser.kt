// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.psi

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiManager
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
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
        if (isBackProvider)
            return false
        return file.isKotlinNotebook
    }
}
