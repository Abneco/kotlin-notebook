// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookScriptingForceUpdateRequestor
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project

internal class ScriptingForceUpdaterK1Factory : NotebookScriptingForceUpdateRequestor.Factory {
    override fun create(project: Project): NotebookScriptingForceUpdateRequestor {
        return ScriptingForceUpdateRequestorK1(project)
    }
}

internal class ScriptingForceUpdateRequestorK1(private val project: Project) : NotebookScriptingForceUpdateRequestor {
    override suspend fun forceUpdateScripting(notebooks: List<BackedNotebookVirtualFile>) {
        JupyterKtScriptingSupport.updateSynchronously(project)

        smartReadAction(project) {
            for (notebook in notebooks) {
                NotebookHighlightingService.getForFile(project, notebook).restartAnalysing()
            }
        }
    }
}