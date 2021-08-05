package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.KotlinJupyterIcons
import javax.swing.Icon

class JupyterKotlinIconPatcher : FileIconPatcher {
    override fun patchIcon(baseIcon: Icon?, virtualFile: VirtualFile?, flags: Int, project: Project?): Icon? {
        if (virtualFile == null || virtualFile.extension != "ipynb" || !virtualFile.isKotlinNotebook) return baseIcon
        return KotlinJupyterIcons.FileIcon
    }
}
