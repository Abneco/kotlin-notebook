// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookScriptingForceUpdateRequestor
import com.intellij.openapi.project.Project

private class ScriptingForceUpdaterK2Factory : NotebookScriptingForceUpdateRequestor.Factory {
    override fun create(project: Project): NotebookScriptingForceUpdateRequestor {
        return ScriptingForceUpdateRequestorK2(project)
    }
}

internal class ScriptingForceUpdateRequestorK2(private val project: Project) : NotebookScriptingForceUpdateRequestor {
    /**
     * For K2, we just need to perform the scripting update and HL will restart next.
     */
    override suspend fun forceUpdateScripting(notebooks: List<BackedNotebookVirtualFile>) {
        for (notebook in notebooks) {
            JupyterCompilerService.getForFile(project, notebook).requestScriptingUpdate()
        }
    }
}