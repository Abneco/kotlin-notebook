// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide

import com.intellij.ide.FileIconProvider
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.KotlinJupyterIcons
import javax.swing.Icon

class KotlinNotebookFileIconProvider: FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        return if (file.isKotlinNotebook) {
            KotlinJupyterIcons.FileIcon
        } else {
            null
        }
    }
}
