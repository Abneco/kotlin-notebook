// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.jupyter.core.jupyter.editor.JupyterServerChooserActionPresentationUpdater
import com.intellij.jupyter.core.jupyter.editor.JupyterServerChooserActionPresentationUpdater.Result
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

class KotlinNotebookServerChooserActionPresentationUpdater : JupyterServerChooserActionPresentationUpdater {
    override fun updatePresentation(
        presentation: Presentation,
        virtualFile: VirtualFile?
    ): Result {
        if (virtualFile == null || !virtualFile.isKotlinNotebook) return Result.Continue

        presentation.isEnabledAndVisible = false
        return Result.Stop
    }
}