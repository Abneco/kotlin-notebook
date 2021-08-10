// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiManager
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileViewProviderFactory

class JupyterKotlinFileViewProviderChooser : JupyterFileViewProviderFactory.Chooser {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean
    ): FileViewProvider {
        return JupyterKotlinFileViewProvider(manager, file, true)
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        return file.isKotlinNotebook
    }
}
