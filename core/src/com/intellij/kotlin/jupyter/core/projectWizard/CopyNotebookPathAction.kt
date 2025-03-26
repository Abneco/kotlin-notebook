// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.projectWizard.common.RECENT_NOTEBOOK_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.io.FileUtil
import java.awt.datatransfer.StringSelection

/**
 * Action to copy the full path of the selected notebook to the clipboard.
 */
class CopyNotebookPathAction : AnAction(), DumbAware {
    init {
        isEnabledInModalContext = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val notebook = e.getData(RECENT_NOTEBOOK_KEY) ?: return
        val path = FileUtil.toSystemDependentName(notebook.path.path)
        CopyPasteManager.getInstance().setContents(StringSelection(path))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(RECENT_NOTEBOOK_KEY) != null
    }
}
