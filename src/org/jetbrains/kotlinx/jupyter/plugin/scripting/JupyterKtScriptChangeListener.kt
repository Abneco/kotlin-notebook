package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType

class JupyterKtScriptChangeListener(project: Project) : ScriptChangeListener(project) {
    private val scriptingSupport = JupyterKtScriptingSupport.getInstance(project)

    override fun documentChanged(vFile: VirtualFile) {
        scriptingSupport.update()
    }

    override fun editorActivated(vFile: VirtualFile) {
        scriptingSupport.update()
    }

    override fun isApplicable(vFile: VirtualFile): Boolean {
        return vFile.fileType is JupyterFileType
    }
}
