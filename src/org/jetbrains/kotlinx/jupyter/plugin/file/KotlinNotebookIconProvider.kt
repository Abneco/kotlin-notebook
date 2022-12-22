// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import javax.swing.Icon

object KotlinNotebookIconProvider: IconProvider(), DumbAware {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is JupyterFile) return null
        return JupyterKotlinIconPatcher.patchIcon(null, element.virtualFile, flags, element.project)
    }
}
