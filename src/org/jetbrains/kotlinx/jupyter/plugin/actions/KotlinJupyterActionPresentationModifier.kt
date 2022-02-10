// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.getVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterDebugAction
import org.jetbrains.plugins.notebooks.jupyter.editor.actions.JupyterActionPresentationModifier

class KotlinJupyterActionPresentationModifier : JupyterActionPresentationModifier {
    override fun modifyPresentation(action: AnAction, event: AnActionEvent) {
        val virtualFile = event.getVirtualFile()

        if (!virtualFile.isKotlinNotebook) return

        if (action.isActionToHide()) {
            event.presentation.isEnabledAndVisible = false
            return
        }
    }

    private fun AnAction.isActionToHide(): Boolean {
        return this is JupyterDebugAction
    }
}
