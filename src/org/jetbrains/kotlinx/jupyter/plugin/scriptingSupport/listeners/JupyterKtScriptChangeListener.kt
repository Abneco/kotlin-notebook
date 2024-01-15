// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType

class JupyterKtScriptChangeListener(project: Project) : ScriptChangeListener(project) {
    override fun documentChanged(vFile: VirtualFile) {
        //scriptingSupport.update()
    }

    override fun editorActivated(vFile: VirtualFile) {
        //scriptingSupport.update()
    }

    override fun isApplicable(vFile: VirtualFile): Boolean {
        return vFile.fileType is JupyterFileType
    }
}
