// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.ide

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
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
