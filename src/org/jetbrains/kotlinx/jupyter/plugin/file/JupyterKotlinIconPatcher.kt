package org.jetbrains.kotlinx.jupyter.plugin.file

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.icons.JupyterKotlinIcons
import org.jetbrains.plugins.notebooks.jackson
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebookSchemaFactory
import javax.swing.Icon

class JupyterKotlinIconPatcher : FileIconPatcher {
    override fun patchIcon(baseIcon: Icon?, virtualFile: VirtualFile?, flags: Int, project: Project?): Icon? {
        if (virtualFile == null || virtualFile.extension != "ipynb") return baseIcon

        val reader = virtualFile.inputStream.reader()
        val json = jackson.readTree(reader) as? ObjectNode ?: return baseIcon
        val schema = JupyterNotebookSchemaFactory.createSchema(json)
        val language = schema.getLanguage(json)
        if (language == "kotlin") return JupyterKotlinIcons.FileType
        return baseIcon
    }
}
