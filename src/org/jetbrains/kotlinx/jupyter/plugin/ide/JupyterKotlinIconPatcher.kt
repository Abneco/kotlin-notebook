// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.ide

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import java.util.*
import javax.swing.Icon

class JupyterKotlinIconPatcher : FileIconPatcher {
    private val cache: MutableSet<VirtualFile> = Collections.synchronizedSet(ContainerUtil.createWeakSet())

    private fun shouldPatch(virtualFile: VirtualFile?): Boolean {
        if (virtualFile == null || virtualFile.extension != "ipynb") return false
        if (cache.contains(virtualFile)) {
            return true
        }
        return virtualFile.isKotlinNotebook
    }

    override fun patchIcon(baseIcon: Icon?, virtualFile: VirtualFile?, flags: Int, project: Project?): Icon? {
        if (shouldPatch(virtualFile)) {
            if (virtualFile != null) {
                cache.add(virtualFile)
            }
            return KotlinJupyterIcons.FileIcon
        }
        return baseIcon
    }
}
