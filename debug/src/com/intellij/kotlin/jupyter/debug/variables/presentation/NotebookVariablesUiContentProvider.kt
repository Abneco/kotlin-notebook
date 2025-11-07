// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.variables.presentation

import com.intellij.kotlin.jupyter.core.editor.appearance.KotlinNotebookToolVariablesUiContentProvider
import com.intellij.kotlin.jupyter.core.editor.appearance.data.KotlinNotebookVariablesToolWindowConfiguration
import com.intellij.kotlin.jupyter.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.ui.content.Content

/**
 * Builds Ui Content using underlying [KotlinNotebookSessionVariablesService]
 * and debug features to populate stack frames
 */
internal class NotebookVariablesUiContentProvider : KotlinNotebookToolVariablesUiContentProvider {
    override fun createVariablesViewContent(configuration: KotlinNotebookVariablesToolWindowConfiguration): Content? {
        /**
         * Always get the tool window from the service. This ensures that the service
         * is aware of the UI panel, preventing it from creating its own duplicate tab
         * in the tool window if it were initialized elsewhere.
         */
        val toolWindowPanel = KotlinNotebookSessionVariablesService
            .getForFile(
                configuration.project,
                configuration.virtualFile
            )
            .getToolWindow(configuration)

        return toolWindowPanel.createContent()
    }
}