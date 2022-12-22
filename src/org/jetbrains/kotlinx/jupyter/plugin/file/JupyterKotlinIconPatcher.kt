package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.KotlinJupyterIcons
import java.util.*
import javax.swing.Icon

object JupyterKotlinIconPatcher : FileIconPatcher, DumbAware {
    private val cache: MutableSet<VirtualFile> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

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
