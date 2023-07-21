// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.ide

import com.intellij.ide.FileIconPatcher
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import javax.swing.Icon

class KotlinNotebookIconProvider: IconProvider(), DumbAware {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is JupyterFile) return null
        val patcher = FileIconPatcher.EP_NAME.findExtension(JupyterKotlinIconPatcher::class.java) ?: return null
        return patcher.patchIcon(null, element.virtualFile, flags, element.project)
    }
}
